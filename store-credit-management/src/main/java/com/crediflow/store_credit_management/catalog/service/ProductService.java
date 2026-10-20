package com.crediflow.store_credit_management.catalog.service;

import com.crediflow.store_credit_management.catalog.domain.model.Product;
import com.crediflow.store_credit_management.catalog.domain.model.ProductStatus;
import com.crediflow.store_credit_management.catalog.dto.ProductDtos.ProductRequest;
import com.crediflow.store_credit_management.catalog.repository.ProductRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product Service
 * @summary
 * Casos de uso sobre productos y servicios: alta, consulta, listado, edición
 * y baja lógica.
 *
 * Todos los métodos reciben el storeId de la tienda que opera (que debe venir
 * de la sesión, nunca de lo que escriba el usuario) y lo usan en CADA consulta
 * para que una tienda no vea productos de otra.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /** Da de alta un producto en la tienda. */
    @Transactional
    public Product register(Long storeId, ProductRequest request) {
        Product product = Product.register(
                storeId,
                request.name(),
                request.description(),
                request.supplier(),
                request.brand(),
                request.unitOfMeasure(),
                request.imageUrl(),
                request.cashPrice(),
                request.creditPrice(),
                request.currency(),
                request.endOfMonthAllowed(),
                request.installmentsAllowed());
        return productRepository.save(product);
    }

    /** Busca un producto de la tienda; si no existe o es de otra, responde 404. */
    @Transactional(readOnly = true)
    public Product get(Long storeId, Long productId) {
        return productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }

    /** Lista paginada de productos; si status es null, trae todos. */
    @Transactional(readOnly = true)
    public Page<Product> list(Long storeId, ProductStatus status, Pageable pageable) {
        return status == null
                ? productRepository.findByStoreId(storeId, pageable)
                : productRepository.findByStoreIdAndStatus(storeId, status, pageable);
    }

    /**
     * Reemplaza los datos editables. Los cambios se guardan al cerrar la
     * transacción. Cambiar un precio NO altera compras ya hechas, porque cada
     * compra guarda su propia copia del precio.
     */
    @Transactional
    public Product update(Long storeId, Long productId, ProductRequest request) {
        Product product = get(storeId, productId);
        product.updateDetails(
                request.name(),
                request.description(),
                request.supplier(),
                request.brand(),
                request.unitOfMeasure(),
                request.imageUrl(),
                request.cashPrice(),
                request.creditPrice(),
                request.currency(),
                request.endOfMonthAllowed(),
                request.installmentsAllowed());
        return product;
    }

    /**
     * Baja lógica. A diferencia de un cliente, un producto se puede dar de baja
     * aunque tenga compras: esas compras conservan su copia de nombre y precio.
     */
    @Transactional
    public Product deactivate(Long storeId, Long productId) {
        Product product = get(storeId, productId);
        if (!product.isActive()) {
            throw new BussinessRuleException("El producto ya está dado de baja");
        }
        product.deactivate();
        return product;
    }
}
