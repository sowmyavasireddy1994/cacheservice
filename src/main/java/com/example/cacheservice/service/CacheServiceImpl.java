package com.example.cacheservice.service;

import com.example.cacheservice.entity.MyEntity;
import com.example.cacheservice.exception.CacheException;
import com.example.cacheservice.repository.MyEntityRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class CacheServiceImpl implements CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheServiceImpl.class);

    private final int maxSize;
    private final long expirationMillis;
    private final MyEntityRepository repository;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Map<Long, CacheEntry> cacheMap;
    private final ScheduledExecutorService scheduler;

    public CacheServiceImpl(
            MyEntityRepository repository,
            @Value("${cache.maxSize:5}") int maxSize,
            @Value("${cache.expirationMillis:60000}") long expirationMillis) {

        this.repository = repository;
        this.maxSize = maxSize;
        this.expirationMillis = expirationMillis;

        this.cacheMap = new LinkedHashMap<Long, CacheEntry>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry> eldest) {
                if (size() > maxSize) {
                    CacheEntry entryToEvict = eldest.getValue();
                    MyEntity toEvict = entryToEvict.getEntity();
                    logger.info("Evicting entity with id {} to database due to size limit.", toEvict.getId());
                    try {
                        repository.save(toEvict);
                    } catch (Exception e) {
                        logger.error("Failed to save evicted entity with id {} to DB: {}", toEvict.getId(), e.getMessage());
                    }
                    return true;
                }
                return false;
            }
        };

        // Schedule periodic cleanup of expired entries
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::removeExpiredEntries, expirationMillis, expirationMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void add(MyEntity e1) throws CacheException {
        if (e1 == null) {
            throw new CacheException("Cannot add a null entity to the cache.");
        }

        rwLock.writeLock().lock();
        try {
            MyEntity entityToStore = repository.save(e1);
            cacheMap.put(entityToStore.getId(), new CacheEntry(entityToStore));
            logger.info("Entity with id {} added/updated in cache.", entityToStore.getId());
        } catch (Exception ex) {
            logger.error("Error adding entity to cache: {}", ex.getMessage());
            throw new CacheException("Error adding entity to cache.", ex);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void remove(MyEntity e1) throws CacheException {
        if (e1 == null || e1.getId() == null) {
            throw new CacheException("Cannot remove an entity without a valid ID.");
        }

        rwLock.writeLock().lock();
        try {
            cacheMap.remove(e1.getId());
            if (repository.existsById(e1.getId())) {
                repository.deleteById(e1.getId());
                logger.info("Entity with id {} removed from cache and DB", e1.getId());
            } else {
                logger.warn("Attempted to remove entity with id {} that does not exist in DB", e1.getId());
            }
        } catch (Exception ex) {
            logger.error("Error removing entity with id {}: {}", e1.getId(), ex.getMessage());
            throw new CacheException("Error removing entity from cache.", ex);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll() {
        rwLock.writeLock().lock();
        try {
            cacheMap.clear();
            repository.deleteAll();
            logger.info("All entities removed from cache and DB");
        } catch (Exception ex) {
            logger.error("Error removing all entities: {}", ex.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public MyEntity get(MyEntity e1) throws CacheException {
        if (e1 == null || e1.getId() == null) {
            throw new CacheException("Cannot get an entity without a valid ID.");
        }

        rwLock.writeLock().lock();
        try {
            CacheEntry entry = cacheMap.get(e1.getId());
            if (entry != null && !entry.isExpired(expirationMillis)) {
                entry.updateAccessTime();
                logger.info("Entity with id {} found in cache", e1.getId());
                return entry.getEntity();
            } else {
                if (entry != null && entry.isExpired(expirationMillis)) {
                    cacheMap.remove(e1.getId());
                }

                Optional<MyEntity> fromDb = repository.findById(e1.getId());
                if (fromDb.isPresent()) {
                    MyEntity entity = fromDb.get();
                    cacheMap.put(entity.getId(), new CacheEntry(entity));
                    logger.info("Entity with id {} fetched from DB and added to cache", e1.getId());
                    return entity;
                } else {
                    throw new CacheException("Entity with id " + e1.getId() + " not found in cache or DB.");
                }
            }
        } catch (Exception ex) {
            logger.error("Error getting entity with id {}: {}", e1.getId(), ex.getMessage());
            throw new CacheException("Error getting entity from cache.", ex);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        rwLock.writeLock().lock();
        try {
            cacheMap.clear();
            logger.info("Cache cleared. DB untouched.");
        } catch (Exception ex) {
            logger.error("Error clearing cache: {}", ex.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void removeExpiredEntries() {
        rwLock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Long, CacheEntry>> iterator = cacheMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, CacheEntry> entry = iterator.next();
                if (entry.getValue().isExpired(expirationMillis)) {
                    iterator.remove();
                    logger.info("Removed expired entity with id {} from cache", entry.getKey());
                }
            }
        } catch (Exception ex) {
            logger.error("Error removing expired entries: {}", ex.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @PreDestroy
    public void onDestroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            logger.info("Scheduler shut down on bean destruction.");
        }
    }

    // ✅ Custom internal CacheEntry class (not Hibernate's!)
    private static class CacheEntry {
        private final MyEntity entity;
        private long lastAccessTime;

        CacheEntry(MyEntity entity) {
            this.entity = entity;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired(long expirationMillis) {
            return (System.currentTimeMillis() - lastAccessTime) > expirationMillis;
        }

        MyEntity getEntity() {
            return entity;
        }
    }
}
