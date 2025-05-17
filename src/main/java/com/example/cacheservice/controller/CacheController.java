package com.example.cacheservice.controller;


import com.example.cacheservice.dto.MyEntityDTO;
import com.example.cacheservice.entity.MyEntity;
import com.example.cacheservice.exception.CacheException;
import com.example.cacheservice.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller to demonstrate the caching operations.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @PostMapping("/add")
    public MyEntityDTO add(@RequestBody MyEntityDTO dto) {
        logger.info("Adding entity to cache: {}", dto);
        MyEntity entity = toEntity(dto);
        try {
            cacheService.add(entity);
        } catch (CacheException e) {
            throw new RuntimeException("Failed to add entity to cache", e);
        }
        return dto;
    }

    @DeleteMapping("/remove")
    public void remove(@RequestBody MyEntityDTO dto) {
        logger.info("Removing entity from cache: {}", dto);
        MyEntity entity = toEntity(dto);
        try {
            cacheService.remove(entity);
        } catch (CacheException e) {
            throw new RuntimeException("Failed to remove entity to cache", e);
        }
    }

    @DeleteMapping("/removeAll")
    public void removeAll() {
        logger.info("Removing all entities from cache and DB");
        cacheService.removeAll();
    }

    @GetMapping("/get/{id}")
    public MyEntityDTO get(@PathVariable Long id) {
        logger.info("Fetching entity from cache/DB with id: {}", id);
        MyEntity entity = null;
        try {
            entity = cacheService.get(new MyEntity(id, null));
        } catch (CacheException e) {
            throw new RuntimeException("Failed to get entity to cache", e);
        }
        return toDTO(entity);
    }

    @PostMapping("/clear")
    public void clear() {
        logger.info("Clearing cache (DB untouched)");
        cacheService.clear();
    }

    private MyEntity toEntity(MyEntityDTO dto) {
        return new MyEntity(dto.getId(), dto.getName());
    }

    private MyEntityDTO toDTO(MyEntity entity) {
        MyEntityDTO dto = new MyEntityDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        return dto;
    }
}
