package com.cai.cache.aop

import com.cai.cache.annotation.GetCache
import com.cai.cache.util.AopCacheUtils
import com.cai.general.core.BaseEntity
import com.cai.general.core.BaseMapper
import com.cai.redis.RedisService
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

import static com.cai.cache.util.AopCacheUtils.*

@Aspect
@Component
class GetCacheAop {

    Logger log = LoggerFactory.getLogger(GetCacheAop.class)

    @Autowired
    RedisService redisService

    @Autowired
    ApplicationContext ac

    @Around(value = "@annotation(cacheValue)")
    Object doAroundGetCache(ProceedingJoinPoint pjp, GetCache cacheValue){
        try{
            if (!cacheValue || !BaseEntity.isAssignableFrom(cacheValue.targetClass()) || !cacheValue.targetClass().DEFINE.cache){
                return pjp.proceed(pjp.getArgs())
            }
            BaseEntity cacheEntity, entity
            Object cacheObj
            String cacheKey = AopCacheUtils.getCacheName(pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(),  pjp.getArgs())
            Object res = redisService.tryAndGetOpJedis{it->
                cacheObj = getCache(cacheValue.targetClass(), it, pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), pjp.getArgs())
                if (!cacheObj || !cacheObj.id || !cacheObj.lastUpdated) return null
                cacheEntity = cacheObj as BaseEntity
                if (!cacheEntity || !cacheEntity.DEFINE || !cacheEntity.DEFINE.mapper ||!cacheEntity.getEntityId()) return null
                BaseMapper mapper = ac.getBean(cacheEntity.DEFINE.mapper as Class)
                entity = mapper.selectById(cacheEntity.getEntityId() as Long) as BaseEntity
                if (entity.lastUpdated == cacheObj.lastUpdated){
                    return cacheObj
                } else{
                    delCache(it, pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), pjp.getArgs())
                    return null
                }
            }
            if (!res){
                res = pjp.proceed(pjp.getArgs())
                redisService.tryOpJedis{it->
                    setCache(res, it, pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), pjp.getArgs())
                }
                return res
            }else{
                log.info("locate cached data {key: $cacheKey}")
                return res
            }
        }catch(Throwable t){
            t.printStackTrace()
        }
    }
}
