package com.cai.cache.annotation


import java.lang.annotation.*

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(value = [ElementType.METHOD])
@interface GetCache {

    Class targetClass()

}