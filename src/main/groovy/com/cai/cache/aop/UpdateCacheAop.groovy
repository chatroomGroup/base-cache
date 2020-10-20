package com.cai.cache.aop

import com.cai.cache.annotation.UpdateCache
import com.cai.cache.util.AopCacheUtils
import com.cai.general.core.BaseEntity
import com.cai.jdbc.mysql.JdbcTemplate
import com.cai.redis.RedisService
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

import java.util.stream.Stream

import static com.cai.redis.RedisService.serialize

@Aspect
@Component
class UpdateCacheAop {

    Logger log = LoggerFactory.getLogger(UpdateCacheAop.class)

    @Autowired
    RedisService redisService

    @Autowired
    ApplicationContext ac

    @Autowired
    JdbcTemplate jdbcTemplate

    @Around(value = "@annotation(cacheValue)")
    Object doAroundFindCache(ProceedingJoinPoint pjp, UpdateCache cacheValue){
        try{
            if (!cacheValue || !BaseEntity.isAssignableFrom(cacheValue.targetClass()) || !cacheValue.targetClass().DEFINE.cache){
                return pjp.proceed(pjp.getArgs())
            }
            Class toClazz = cacheValue.targetClass()
            BaseEntity cacheEntity, entity
            List<Object> cacheObjs
            List<Map> validateMaps
            String cacheKey = AopCacheUtils.getCacheName(pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(),  pjp.getArgs())
            redisService.tryOpJedis{it->
                AopCacheUtils.delCache(it, pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), pjp.getArgs())
            }
            Object res = pjp.proceed(pjp.getArgs())
            if (res){
                List<Object> objs = AopCacheUtils.executeSqlData(jdbcTemplate, cacheValue.validateSql(), pjp.args?.toList(), cacheValue.param().split("\\,")?.toList(), toClazz)
                if (!objs || objs.empty) return res
                def statu = redisService.tryAndGetOpJedis{it->
                    String[] param = objs.stream().flatMap{ Stream.of(it.cacheKey as String, serialize(it) as String)}.toArray()
                    it.mset(param)
                }
                if(statu)
                    log.info("fill cached data {key: $cacheKey}")
            }
            return res
        }catch(Throwable t){
            t.printStackTrace()
        }
    }
}
