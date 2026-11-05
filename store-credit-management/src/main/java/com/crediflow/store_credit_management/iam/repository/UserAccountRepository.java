package com.crediflow.store_credit_management.iam.repository;

import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import com.crediflow.store_credit_management.iam.domain.model.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User Account Repository
 * @summary
 * Acceso a datos de cuentas de usuario. Las consultas que usa un
 * administrador de tienda reciben el storeId, de modo que nunca pueda ver ni
 * tocar cuentas de otra tienda. NO se usa findById(...) para eso.
 *
 * @since 1.0.0
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** Para el login: el nombre se busca ya normalizado (en minúsculas). */
    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /** ¿Existe ya algún usuario con ese rol? Decide si hay que crear el primer administrador del sistema. */
    boolean existsByRole(UserRole role);

    /** Busca una cuenta SOLO si pertenece a la tienda indicada (evita IDOR). */
    Optional<UserAccount> findByIdAndStoreId(Long id, Long storeId);

    /** Lista paginada de las cuentas de una tienda. */
    Page<UserAccount> findByStoreId(Long storeId, Pageable pageable);

    /** Cuántas cuentas de un rol y estado tiene la tienda (para no dejarla sin administrador). */
    long countByStoreIdAndRoleAndStatus(Long storeId, UserRole role, UserStatus status);
}