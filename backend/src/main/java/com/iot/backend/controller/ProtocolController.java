package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.protocol.ProtocolHandlerFactory;
import com.iot.backend.protocol.ProtocolMetadata;
import com.iot.backend.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protocols")
public class ProtocolController {

    @Autowired
    private ProtocolHandlerFactory protocolHandlerFactory;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<ProtocolMetadata>> listProtocols() {
        return Result.success(protocolHandlerFactory.listEnabledProtocols());
    }

    @GetMapping("/installed")
    public Result<List<ProtocolMetadata>> listInstalledProtocols() {
        return Result.success(protocolHandlerFactory.listInstalledProtocols());
    }

    @PostMapping("/definitions")
    public Result<ProtocolMetadata> importDefinition(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @RequestBody ProtocolMetadata metadata) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "only admin can import protocol definitions");
        }
        try {
            return Result.success(protocolHandlerFactory.importDefinition(metadata));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        } catch (Exception ex) {
            return Result.error(500, ex.getMessage());
        }
    }
}
