package com.cai.cache.annotation


import java.lang.annotation.*

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(value = [ElementType.METHOD])
@interface FindCache {

    Class targetClass()

    String validateSql()

    String param() default ""
}