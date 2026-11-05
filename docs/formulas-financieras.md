# Fórmulas financieras de CrediFlow

Este documento reúne **todas las fórmulas de finanzas que usa el sistema**, con una explicación breve, un ejemplo numérico y el lugar exacto del código donde se aplican. Está pensado para que quien revise el proyecto (curso de *Finanzas e Ingeniería Económica*, portafolio) pueda ir de la fórmula al código y del código a la fórmula.

> Las rutas de código son relativas a `src/main/java/com/crediflow/store_credit_management/`.
> Los números de los ejemplos se verificaron contra el código y contra un cálculo independiente; los importes están redondeados a 2 decimales.

---

## 0. Convenciones generales

| Convención | Valor | Dónde vive |
|---|---|---|
| Año comercial | **360 días** | `RateMath.DAYS_IN_YEAR` |
| Mes comercial | **30 días** | `RateMath.DAYS_IN_MONTH` |
| Tasa de referencia | **TEA** (tasa efectiva anual) | todo el motor trabaja con TEA |
| Redondeo | **HALF_UP** | `RateMath.ROUNDING` |
| Importes | **2 decimales** | `RateMath.roundMoney` |
| Tasas | **9 decimales** | `RateMath.RATE_SCALE` |
| Cálculo interno | `BigDecimal` con `MathContext.DECIMAL128` (34 dígitos) | `InstallmentScheduler`, `InterestRate` |
| Potencia fraccionaria | `Math.expm1` / `Math.log1p` (en `double`) | `RateMath.rawRateForDays` |

**Por qué la potencia fraccionaria usa `double`:** `BigDecimal` no tiene potencias con exponente decimal. El error de `double` (~1e-16) es mucho menor que los 9 decimales con que se redondea la tasa, así que no afecta ningún importe. `expm1/log1p` son además estables cuando la tasa es pequeña.

**Dónde está el motor:** el paquete `credit/engine` es **Java puro** (no usa Spring ni JPA), por eso se puede probar sin levantar la aplicación. Sus pruebas están en `src/test/java/.../credit/engine/` (`RateMathTest`, `InterestCalculatorTest`, `InstallmentSchedulerTest`, `BillingCalendarTest`). Las entidades **nunca** calculan intereses: reciben los números ya calculados desde los servicios.

---

## 1. Conversión de la tasa pactada a TEA

El administrador de la tienda pacta la tasa de cada cliente como **efectiva** o **nominal**. El sistema la convierte **una sola vez**, al guardar la condición de crédito, y desde entonces todo el motor usa la TEA. Los datos originales se conservan solo como historial.

### 1.1 Tasa efectiva de un período → TEA

```
TEA = (1 + i)^(360 / díasDelPeríodo) − 1
```

- `i`: tasa efectiva del período (por ejemplo 0.018 mensual), `díasDelPeríodo`: 360 (anual), 30 (mensual) o 1 (diaria).
- Ejemplo: 24 % efectiva anual → TEA = **0.240000000**.

**Código:** `shared/domain/model/valueobjects/InterestRate.java` → `InterestRate.fromEffective(...)`.

### 1.2 Tasa nominal con capitalización → TEA

```
TNA = j · (360 / díasDelPeríodo)          (tasa nominal llevada a un año)
m   = 360 / díasDeCapitalización           (veces que capitaliza al año)
TEA = (1 + TNA / m)^m − 1
```

- Ejemplo (el de la tasa compensatoria de las pruebas): 36 % nominal anual, capitalización mensual → `m = 12`, `TNA/m = 0.03` → TEA = 1.03¹² − 1 = **0.425760887**.
- Ejemplo (tasa moratoria): 48 % nominal anual, capitalización mensual → `0.04` mensual → TEA = **0.601032219**.

**Código:** `InterestRate.fromNominal(...)`. La condición de crédito la construye a partir de la petición en `clients/dto/ClientDtos.java` (`CreateCreditConditionRequest`) y la guarda en `clients/domain/model/CreditCondition.java`, que expone `compensatoryTea()` y `lateTea()` para el resto del sistema.

> Una tasa en cero es válida: el motor la trata aparte (ver 5.1, caso `i = 0`).

---

## 2. Tasa equivalente para un número de días

Para cualquier plazo en días, la tasa efectiva equivalente a una TEA es:

```
i(d) = (1 + TEA)^(d / 360) − 1
```

- Es **interés compuesto** a nivel de la conversión de tasas (la TEA ya capitaliza), pero el interés que se cobra con ella es **simple** sobre la base (ver 6 y 7).
- Ejemplo con la TEA compensatoria 0.425760887:
  - 11 días → `i = 0.010897174`
  - 14 días → `i = 0.013889685`
  - 21 días → `i = 0.020906708`
  - 30 días → `i = 0.030000000` (coincide con el 3 % mensual, como debe)

**Código:** `credit/engine/RateMath.java` → `rateForDays(tea, días)` (redondeada a 9 decimales) y el método privado `rawRateForDays` (sin redondear).

### 2.1 Tasa mensual comercial

```
i = (1 + TEA)^(30 / 360) − 1
```

Es la tasa de cada cuota de la compra a plazos.

**Código:** `RateMath.monthlyRate(tea)`.

### 2.2 Factor de capitalización

```
f(d) = (1 + TEA)^(d / 360)
```

Se usa para capitalizar el período de gracia (sección 4).

**Código:** `RateMath.growthFactor(tea, días)`.

---

## 3. Calendario de facturación: ciclo, corte y vencimientos

Cada cliente tiene un **día de corte**, una **hora de corte** y un **día de pago**. Estas reglas deciden a qué ciclo pertenece cada compra y cuándo vence cada cuota.

| Regla | Descripción |
|---|---|
| Corte exclusivo | Una compra es del ciclo actual solo si ocurre **estrictamente antes** de `fechaCorte + horaCorte`. El instante exacto del corte ya pertenece al ciclo siguiente. |
| Pago posterior al corte | La fecha de pago siempre es posterior a la fecha de corte. Si el día de pago cae antes o el mismo día, se usa el mes siguiente. |
| Mes corto | Si el día pactado no existe en el mes (30 en febrero, por ejemplo), se usa el **último día del mes**. |
| Vencimiento de la cuota *j* | Un mes después de la fecha de pago base por cada cuota: `mes(pagoBase) + j`, con el día de pago ajustado. |

Ejemplo: corte día 20 a las 18:00, pago día 26.
- Compra el 15/09 10:30 → ciclo con corte 20/09 y pago **26/09**.
- Compra el 22/09 10:00 (después del corte) → ciclo con corte 20/10 y pago **26/10** (regla de arrastre de ciclo).
- Cuotas de la compra del 15/09: 26/10, 26/11 y 26/12.

**Código:** `credit/engine/BillingCalendar.java`
- `cycleFor(...)`: ciclo de una compra.
- `adjustDay(...)`: ajuste de fin de mes.
- `installmentDueDate(...)`: vencimiento de cada cuota.
- `lastClosedCycle(...)`: último ciclo cuyo corte ya pasó (lo usa la generación del estado de cuenta).

---

## 4. Compra a plazos: capitalización de la gracia y método francés

Una compra en cuotas usa el **método francés vencido** con mes comercial de 30 días. Antes de calcular las cuotas se **capitaliza el período de gracia** (los días entre la compra y la fecha de pago del ciclo).

### 4.1 Días de gracia

```
g = díasCalendario(fechaCompra, fechaPagoDelCiclo)
```

Ejemplo: del 15/09 al 26/09 → **g = 11 días**.

**Código:** `credit/engine/InterestCalculator.daysBetween(...)`, usado en `InstallmentScheduler.generate(...)` y en `credit/service/PurchaseService.register(...)` (guarda `graceDays` en la compra).

### 4.2 Capital capitalizado

```
K = monto × (1 + TEA)^(g / 360)
```

Ejemplo: monto 300.00, g = 11 → factor 1.010897174 → **K = 303.27** (los intereses de gracia son 3.27).

**Código:** `InstallmentScheduler.generate(...)`, con `RateMath.growthFactor`.

### 4.3 Cuota constante (método francés)

```
C = K · i · (1 + i)^n / ((1 + i)^n − 1)
```

- `i`: tasa mensual (sección 2.1), `n`: número de cuotas.
- Si `i = 0`, la cuota es simplemente `K / n`.
- Ejemplo: K = 303.27, i = 0.03, n = 3 → **C = 107.22**.

**Código:** `InstallmentScheduler.constantPayment(...)`.

### 4.4 Cronograma de amortización

Para cada cuota *j*, con `S` el saldo al inicio de la cuota:

```
interés_j       = S × i
amortización_j  = C − interés_j
saldo_final     = S − amortización_j
```

**La última cuota amortiza todo el saldo restante**, de modo que la suma de amortizaciones es exactamente K (sin descuadres de céntimos).

Cronograma del ejemplo (compra de 300.00 a 3 cuotas, TEA 42.5760887 %):

| Cuota | Vence | Saldo inicial | Interés | Capital | Cuota |
|---|---|---|---|---|---|
| 1 | 26/10/2026 | 303.27 | 9.10 | 98.12 | **107.22** |
| 2 | 26/11/2026 | 205.15 | 6.15 | 101.07 | **107.22** |
| 3 | 26/12/2026 | 104.08 | 3.12 | 104.08 | **107.20** |

Total a pagar: **321.64**. La última cuota es 107.20 y no 107.22 porque absorbe el redondeo (por eso `Σ capital = 303.27` exacto).

**Código:** `InstallmentScheduler.generate(...)` devuelve un `InstallmentPlan` con la lista de `Installment` (`credit/engine/InstallmentPlan.java`, `Installment.java`). `PurchaseService.financedInstallments(...)` lo traduce a las cuotas que guarda `Purchase`.

> **Dónde queda el interés de gracia:** va dentro de `K`, y por lo tanto se reparte en los intereses de las cuotas. No se cobra aparte.

---

## 5. Compra a fin de mes (pago único)

El cliente paga el total en la fecha de pago de su ciclo. Se cobra **interés compensatorio simple** por los días entre la compra y esa fecha:

```
interés = capital × [ (1 + TEA)^(d / 360) − 1 ]
cuota única = capital + interés
```

Ejemplos (TEA 0.425760887, pago 26/09):

| Compra | Capital | Días | Tasa del período | Interés |
|---|---|---|---|---|
| 05/09 | 150.00 | 21 | 0.020906708 | **3.14** |
| 12/09 | 230.50 | 14 | 0.013889685 | **3.20** |
| 15/09 | 100.00 | 11 | 0.010897174 | **1.09** |
| 22/09 (después del corte → pago 26/10) | 100.00 | 34 | 0.034067419 | **3.41** |

**Código:** `credit/engine/InterestCalculator.endOfMonthInterest(...)` (que usa `interestForDays`), llamado desde `PurchaseService.endOfMonthInstallment(...)`.

---

## 6. Interés moratorio

Si una cuota se paga después de su vencimiento, se cobra mora **simple** (no se capitaliza día a día) sobre el monto exigible de la cuota, con la **TEA moratoria de la compra** (la tasa vigente cuando se hizo esa compra):

```
díasMora  = max(0, fechaPago − fechaVencimiento)       (el día de vencimiento no cuenta)
mora      = montoCuota × [ (1 + TEAmora)^(díasMora / 360) − 1 ]
```

- `montoCuota` es capital + interés compensatorio de esa cuota. **La mora no genera mora**: nunca se calcula sobre mora anterior.
- Ejemplo: TEA moratoria 0.601032219, cuota de 386.84, 4 días de atraso → tasa 0.005243126 → mora **2.03**.
- Ejemplo con la cuota del cronograma anterior (107.22) a 30 días de atraso con 48 % nominal mensual (4 % mensual): mora = 107.22 × 0.04 = **4.29**.
- La mora **no consume línea de crédito** (solo el capital pendiente la consume).

**Código:** `InterestCalculator.lateDays(...)` y `InterestCalculator.lateInterest(...)`. Se aplica en dos momentos distintos:

| Momento | Qué calcula | Dónde |
|---|---|---|
| Al emitir el estado de cuenta | Mora **proyectada** hasta la fecha de pago del ciclo, para las cuotas ya vencidas que se arrastran | `credit/service/StatementService.toDraft(...)` |
| Al registrar el pago | Mora **real**, con los días reales entre vencimiento y fecha de pago | `credit/service/PaymentService.payLocked(...)` |

El monto que el cliente debe pagar es el **real**, no el proyectado; por eso el pago exige el monto exacto recalculado a esa fecha.

---

## 7. Imputación del pago

Cuando el cliente paga, cada cuota se reparte en este orden fijo:

```
1.º  interés moratorio
2.º  interés compensatorio
3.º  capital
```

Como **no hay pagos parciales**, cada pago cubre las tres partes completas de todas las cuotas del estado de cuenta:

```
totalCuota  = mora + interésCompensatorio + capital
totalPago   = Σ totalCuota   (de todas las cuotas del estado de cuenta)
```

Ejemplo: mora 2.03 + interés 6.34 + capital 380.50 = **388.87**.

**Código:** `credit/engine/PaymentAllocation.java` (record que valida que ninguna parte sea negativa y suma el total) y su uso en `PaymentService.payLocked(...)`, que además guarda el desglose por cuota en `credit/domain/model/Payment.java` / `PaymentLine.java`.

> Ejemplo completo del estado de cuenta de octubre: pagar el 26/10 (sin atraso) cuesta 315.18; pagarlo el 05/11 (con 40, 10 y 10 días de atraso en sus tres cuotas) cuesta 318.75. La diferencia, 7.04 en total, es la mora real. El sistema rechaza (422) un monto distinto y responde cuál es el total correcto.

---

## 8. Línea de crédito revolvente

```
usado      = Σ capital pendiente de las cuotas en estado PENDING
disponible = max(0, límite − usado)
```

- El **límite** se pacta por cliente en la condición de crédito.
- El capital de una cuota **solo** deja de contar cuando la cuota se paga. Pagar una cuota libera su capital; la mora y el interés compensatorio no ocupan cupo.
- Se **calcula** cada vez, no se guarda un contador: no puede quedar desactualizado ni contradecirse.
- Una compra se rechaza (422) si su monto financiado supera el disponible.

Ejemplo (límite 1 000.00): tras la compra de 300.00 a 3 cuotas, `usado = 303.27` y `disponible = 696.73`. Tras pagar la cuota 1 (capital 98.12): `usado = 205.15` y `disponible = 794.85`.

**Código:**
- `credit/service/CreditLineService.java` → `get(...)` (resumen para la API) y `availableAmount(...)` (lo usa `PurchaseService.register`).
- `credit/repository/PurchaseInstallmentRepository.java` → `outstandingPrincipal(...)` y `sumPrincipalByStatus(...)`.

---

## 9. Estado de cuenta

```
líneas = cuotas PENDING con vencimiento ≤ fechaDePagoDelCiclo
         (las vencidas arrastradas + las del ciclo)
totalEstado = Σ ( capital + interés compensatorio + mora proyectada )
```

- Se emite un estado por **cliente y ciclo** (idempotente: pedirlo dos veces no lo duplica).
- El nuevo estado reemplaza al anterior `ISSUED`, que pasa a `SUPERSEDED`.
- No se emiten estados vacíos.

**Código:** `credit/service/StatementService.java` (`generate`, `generateIfDue`), `credit/domain/model/Statement.java` y `StatementLine.java`. El ciclo a facturar sale de `BillingCalendar.lastClosedCycle(...)`.

---

## 10. Resumen: fórmula → código

| # | Fórmula | Clase / método |
|---|---|---|
| 1.1 | TEA desde tasa efectiva | `InterestRate.fromEffective` |
| 1.2 | TEA desde tasa nominal | `InterestRate.fromNominal` |
| 2 | Tasa equivalente a *d* días | `RateMath.rateForDays` |
| 2.1 | Tasa mensual comercial | `RateMath.monthlyRate` |
| 2.2 | Factor de capitalización | `RateMath.growthFactor` |
| 3 | Ciclo, corte, vencimientos | `BillingCalendar.cycleFor`, `adjustDay`, `installmentDueDate`, `lastClosedCycle` |
| 4.1 | Días de gracia | `InterestCalculator.daysBetween` |
| 4.2–4.4 | Capital capitalizado, cuota francesa, cronograma | `InstallmentScheduler.generate`, `constantPayment` |
| 5 | Interés de compra a fin de mes | `InterestCalculator.endOfMonthInterest` |
| 6 | Días de mora e interés moratorio | `InterestCalculator.lateDays`, `lateInterest` |
| 6 | Mora proyectada (estado de cuenta) | `StatementService.toDraft` |
| 6–7 | Mora real e imputación (pago) | `PaymentService.payLocked`, `PaymentAllocation` |
| 8 | Línea de crédito | `CreditLineService`, `PurchaseInstallmentRepository.outstandingPrincipal` |
| 9 | Total del estado de cuenta | `StatementService`, `Statement` |

---

## 11. Decisiones financieras que sostienen estas fórmulas

- **30/360 fijo** para meses y año: requisito del enunciado y convención usual de la banca peruana.
- **La TEA como tasa canónica**: se convierte una vez y se conserva el dato original solo como historial.
- **Interés simple sobre la base** para fin de mes y mora (no se capitaliza día a día); la conversión de tasas sí es compuesta.
- **Gracia capitalizada** en compras a plazos, tal como pide el ejemplo del enunciado (15/09 con corte 20/09 y pago 26/09 → 11 días de gracia y cuotas el 26/10, 26/11 y 26/12).
- **Sin pagos parciales**: el pago siempre salda el estado completo, con monto exacto.
- **Imputación mora → interés → capital**, y **la mora no cuenta contra el cupo**.
- **Redondeo HALF_UP a 2 decimales** en cada importe que se guarda; la última cuota absorbe el descuadre.

## 12. Cómo verificar los números

1. Con Swagger, registra una condición de crédito con 36 % nominal anual capitalizable mensualmente: debe devolver `tea: 0.425760887`.
2. Registra una compra a 3 cuotas de 300.00 el `2026-09-15T10:30:00`: debe dar `financedAmount 303.27`, cuotas que vencen el 26/10, 26/11 y 26/12, y `totalToPay 321.64`.
3. Ejecuta `mvn test`: las pruebas del paquete `credit/engine` reproducen los ejemplos de este documento.
