package com.crediflow.store_credit_management.stores.service;

import com.crediflow.store_credit_management.stores.domain.model.BusinessType;
import com.crediflow.store_credit_management.stores.repository.BusinessTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Business Type Seeder
 * @summary
 * Precarga el catálogo de giros de negocio al arrancar. Es idempotente: solo
 * inserta los que faltan y nunca borra ni modifica nada, así que puede correr
 * en cada arranque.
 *
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
class BusinessTypeSeeder implements ApplicationRunner {

    private static final List<String> DEFAULT_TYPES = List.of(
            "Bodega", "Frutería", "Carnicería", "Panadería", "Peluquería", "Spa", "Otro");

    private final BusinessTypeRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        DEFAULT_TYPES.stream()
                .filter(name -> !repository.existsByName(name))
                .map(BusinessType::of)
                .forEach(repository::save);
    }
}
