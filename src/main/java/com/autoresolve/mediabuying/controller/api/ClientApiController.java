package com.autoresolve.mediabuying.controller.api;

import com.autoresolve.mediabuying.model.dto.ClientDTO;
import com.autoresolve.mediabuying.service.ClientListService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
public class ClientApiController {

    private final ClientListService clientListService;

    public ClientApiController(ClientListService clientListService) {
        this.clientListService = clientListService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MEDIA_ANALYST','VIEWER')")
    public ResponseEntity<Page<ClientDTO>> getClients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "outlookScore") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Page<ClientDTO> result = clientListService.getClients(page, size, sort, dir);
        return ResponseEntity.ok(result);
    }
}
