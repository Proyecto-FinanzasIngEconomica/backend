# Sistema de Control de Cuenta Corriente para Créditos Comerciales

## Descripción del proyecto

Este proyecto es un **backend REST** (Java + Spring Boot, con diseño DDD táctico y
estratégico) que digitaliza el "cuaderno de fiado" de pequeños comercios (bodegas,
fruterías, carnicerías, panaderías, peluquerías, spas, etc.), permitiéndoles otorgar y
administrar **crédito directo a sus clientes** sobre las compras de productos y servicios
que ofrecen.

El sistema es **multi-tenant**: múltiples tiendas de distintos rubros operan
simultáneamente sobre la misma plataforma, cada una con sus propios productos, clientes,
condiciones crediticias y estados de cuenta, sin visibilidad cruzada entre ellas.

### Roles del sistema

- **Administrador del sistema**: da de alta/baja tiendas.
- **Administrador del negocio**: administra productos/servicios y clientes de su tienda,
  incluyendo las condiciones de crédito pactadas con cada cliente.
- **Cliente**: consulta su historial de compras, deuda vigente y estados de cuenta.

### Alcance funcional

- Gestión de tiendas, productos/servicios y clientes por tienda.
- Registro de compras al crédito, en dos modalidades:
  - **A fin de mes** (pago único en la fecha de pago pactada).
  - **En cuotas**, bajo el **método francés vencido simple ordinario**.
- Generación automática, en la fecha de corte, del **estado de cuenta** de cada cliente:
  compras del período, intereses compensatorios acumulados e intereses moratorios (si
  aplica) sobre saldos vencidos.
- Validación de **línea de crédito revolvente** por cliente y por tienda.

### Fuera de alcance

- Control de inventarios / stock.
- Impuestos (renta o ventas).
- Conversión de tipo de cambio entre monedas (ver decisión #13 más abajo).

---

## Decisiones financieras

El enunciado del curso deja deliberadamente varios puntos abiertos a interpretación
(especialmente los relacionados a convenciones de mercado financiero). A continuación se
documentan las decisiones tomadas, junto con su justificación, para que cualquier persona
que revise el código entienda **por qué** el motor de cálculo se comporta como se
comporta.

### 1. Días por mes en el cronograma de cuotas: fijo en 30 días

**Decisión:** para el cálculo de la cuota francesa (amortización), cada mes se trata
siempre como un mes comercial de 30 días, sin importar el mes calendario real.

**Justificación:** es un requisito explícito y no negociable del enunciado del curso
("considerando meses de 30 días"), y además es coherente con la convención comercial
estándar del sistema financiero peruano para expresar tasas y plazos.

### 2. Base anual para la tasa diaria/mensual: sistema 30/360

**Decisión:** la conversión de la tasa efectiva anual pactada a una tasa diaria/mensual
equivalente permite elegir, será fija para todo cliente en 30/360.

**Justificación:** el estándar de mercado en Perú para expresar la TEA es base 360 (así lo
usan las entidades financieras supervisadas por la SBS).

### 3. Tipo de tasa pactada: TEA (Tasa Efectiva Anual)

**Decisión:** la tasa de interés que se registra por cliente es una TEA.

**Justificación:** es el indicador que el sistema financiero peruano usa como referencia
principal para pactar y comparar créditos (a diferencia de la TNA, que es solo
referencial y no incorpora capitalización), según la propia normativa y práctica
supervisada por la SBS.

### 4. Capitalización del período de gracia en compras a cuotas

**Decisión:**
- Si la compra ocurre **antes** de la fecha de corte, el período de gracia va desde la
  fecha de compra hasta la fecha de pago del ciclo al que pertenece esa compra (el mismo
  ciclo que le correspondería a una compra a fin de mes hecha ese día). La deuda se
  capitaliza por esos días antes de aplicar la fórmula francesa, y las cuotas vencen en
  los ciclos de pago siguientes a ese primer ciclo.
- Si la compra ocurre **después** del corte, todo el cálculo se recorre un ciclo: el
  período de gracia llega hasta la fecha de pago del ciclo siguiente, y las cuotas
  comienzan un ciclo después de ese.

**Justificación:** esta regla es la única que reproduce exactamente el ejemplo numérico
del propio enunciado (compra 15/set, corte 20/set, pago día 26 → 11 días de gracia,
cuotas el 26/oct, 26/nov y 26/dic), y es simétrica con la regla de arrastre de ciclo que
ya aplica a las compras a fin de mes realizadas después del corte.

### 5. Frontera exacta del corte (fecha y hora)

**Decisión:** el instante del corte es un límite **exclusivo** para el ciclo actual: si
`fechaHoraCompra < fechaHoraCorte`, la compra pertenece al ciclo vigente; si es igual o
posterior, pasa al ciclo siguiente.

**Justificación:** es la convención más común en sistemas de facturación por corte
(tarjetas de crédito, servicios), y elimina cualquier ambigüedad de concurrencia: el
mismo instante de ejecución del proceso de corte no puede quedar "dentro y fuera" del
ciclo a la vez.

### 6. Interés moratorio: simple, sobre saldo vencido, cobrado aparte

**Decisión:** el interés moratorio se calcula de forma **simple** (no capitalizable día a
día) sobre el saldo total que debió pagarse, por los días de atraso, aplicando la tasa
moratoria pactada. Se cobra como concepto aparte y **no consume línea de crédito
adicional** (a diferencia del capital vencido, que sí sigue descontando cupo mientras está
impago).

**Justificación:** coincide con el tratamiento del interés moratorio en el ordenamiento
peruano: la normativa que rige las tasas de interés establece expresamente la prohibición
de capitalizar intereses por mora, y el Código Civil dispone que el interés moratorio se
computa desde el día en que el deudor incurre en mora, de forma independiente al interés
compensatorio ya pactado.

### 7. Precisión numérica

**Decisión:** todos los cálculos internos del motor financiero se realizan con una escala
de **7 decimales**; el resultado se redondea a **2 decimales** solo para presentación,
usando `RoundingMode.HALF_UP`. La última cuota del cronograma absorbe cualquier diferencia
de redondeo acumulada, de modo que la suma de cuotas cuadre exactamente con la deuda
capitalizada.

**Justificación:** trabajar con mayor precisión interna que la de presentación evita el
arrastre de error de redondeo entre pasos intermedios del cálculo (conversión de tasa,
capitalización, amortización). `HALF_UP` es el modo de redondeo más extendido en cálculos
contables y financieros en Perú. Absorber el descuadre en la última cuota es la práctica
estándar en sistemas de amortización para garantizar que el total cobrado sea exactamente
igual al total adeudado.

### 8. Fecha de pago en meses con menos días

**Decisión:** si el día de pago pactado no existe en un mes calendario dado (por ejemplo,
día 31 en abril, o día 30/31 en febrero), se usa el **último día calendario** de ese mes.

**Justificación:** es la práctica de mercado más extendida en productos de crédito para
resolver esta situación, evitando fechas de vencimiento inválidas o ambiguas.

### 9. No se permiten pagos parciales

**Decisión:** el cliente paga el **total del estado de cuenta del período** en un único
movimiento; no se admite el pago fraccionado de la deuda del período.

**Justificación:** es un requisito explícito del enunciado del curso, y simplifica el
modelo de agregado de "pago" al no tener que representar estados intermedios de deuda
parcialmente cubierta dentro de un mismo período.

### 10. Arrastre de deuda vencida

**Decisión:** si el cliente no paga en la fecha pactada, el listado de pago vencido queda abierto de forma independiente y se liquida por separado (con su propio interés moratorio), sin fusionarse con el listado que se genere en el siguiente corte. Las compras nuevas del ciclo siguiente generan su propio listado independiente. El saldo vencido sigue consumiendo línea de crédito hasta que se cancela, y no bloquea automáticamente nuevas compras mientras haya cupo disponible.

**Justificación:** de las dos formas razonables de modelar el arrastre (fusionar la deuda vencida dentro del siguiente listado, o mantenerla como una obligación independiente), se optó por la segunda porque simplifica el agregado de "listado de pago" — cada listado corresponde a un único corte y se liquida como una unidad propia — y facilita distinguir con claridad, para efectos de reporte y auditoría, qué intereses moratorios corresponden a qué período de mora, sin mezclar deudas de distintos orígenes en un solo monto exigible.

### 11. Límite de crédito: modelo de línea revolvente

**Decisión:** al otorgar una compra a cuotas, el **monto total financiado** (no solo la
cuota mensual) descuenta el cupo de crédito disponible durante todo el plazo, liberándose
gradualmente conforme se pagan las cuotas.

**Justificación:** es el modelo de riesgo estándar usado por productos de crédito
revolvente (tarjetas de crédito, líneas de consumo) en el sistema financiero, y es el que
mejor protege al comercio de un sobreendeudamiento del cliente frente a compras
simultáneas a distintos plazos.

### 12. Alcance de la relación crediticia: por tienda-cliente

**Decisión:** el perfil crediticio (tasa pactada, moneda, límite de crédito, plazo
máximo, fecha de corte, fecha de pago) pertenece a la **relación entre una tienda y un
cliente**, no al cliente de forma global.

**Justificación:** el enunciado exige que el sistema sea multi-tenant y que cada tienda
administre sus propios clientes con sus propias condiciones; una misma persona puede ser
cliente de varias tiendas con perfiles de riesgo completamente independientes entre sí.

### 13. Moneda: PEN fijo, sin motor de conversión de divisas

**Decisión:** el crédito puede pactarse en soles (PEN) o dólares (USD), a elección del establecimiento por cada cliente. El Value Object Money incluye el campo currency, y todas las compras, intereses, cuotas y pagos de una misma condición de crédito se calculan y registran en la misma moneda pactada. El backend no implementa conversión de tipo de cambio entre monedas: no existe ninguna operación que convierta un monto de PEN a USD o viceversa dentro del sistema.

**Justificación:** el enunciado original ya contemplaba la "moneda del crédito" como un dato configurable por cliente, por lo que soportar más de una moneda es coherente con el alcance original (no es una ampliación forzada). Lo que sigue estando fuera de alcance es la conversión entre monedas, ya que introduciría problemas no pedidos por el enunciado (fuente y actualización del tipo de cambio, fecha de referencia para la conversión), de forma similar a como se excluye el cálculo de impuestos. Cualquier necesidad de comparar o convertir montos entre monedas distintas es responsabilidad de una capa externa a este backend.

### 14. Prelación de imputación de pagos

**Decisión:** cuando el cliente paga el total exigible de un listado (incluyendo mora, si la hubiera), el monto pagado se imputa en este orden: primero al interés moratorio, luego al interés compensatorio, y finalmente al capital.

**Justificación:** es el orden estándar de imputación de pagos en obligaciones dinerarias (cubrir primero el costo del incumplimiento, luego el costo financiero pactado, y por último el principal), y es coherente con el artículo 1257 del Código Civil peruano, que establece que quien deba capital, gastos e intereses no puede, sin consentimiento del acreedor, aplicar el pago al capital antes que a los gastos e intereses.

---

## Stack técnico

- **Java + Spring Boot**, arquitectura **DDD** (bounded contexts + capas domain /
  application / infrastructure / interfaces).
- Documentación de API con **springdoc-openapi** (Swagger UI).
- Persistencia con **Spring Data JPA**.
- Motor de base de datos y dependencias adicionales: en evaluación/discusión durante el
  desarrollo (ver decisiones de infraestructura, documentadas en fases posteriores del
  proyecto).

---

## Estado del proyecto

Este README se actualizará progresivamente conforme avancen las fases de diseño
estratégico, diseño táctico, motor financiero, infraestructura, seguridad y calidad.
