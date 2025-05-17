package com.example.cacheservice.service;


import com.example.cacheservice.entity.MyEntity;
import com.example.cacheservice.exception.CacheException;

public interface CacheService {

    void add(MyEntity e1) throws CacheException;

    void remove(MyEntity e1) throws CacheException;

    void removeAll();

    MyEntity get(MyEntity e1) throws CacheException;

    void clear();
}
