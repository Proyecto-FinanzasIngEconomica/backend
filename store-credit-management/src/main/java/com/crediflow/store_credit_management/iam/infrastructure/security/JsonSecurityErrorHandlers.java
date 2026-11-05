package com.crediflow.store_credit_management.iam.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Json Security Error Handlers
 * @summary
 * Respuestas de seguridad con el mismo formato que el resto de errores de la
 * API: {"message": "..."}. 401 si no hay sesión válida, 403 si hay sesión pero
 * no permiso.
 *
 * @since 1.0.0
 */
@Component
class JsonSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Debes iniciar sesión con un token válido");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "No tienes permiso para esta operación");
    }

    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
