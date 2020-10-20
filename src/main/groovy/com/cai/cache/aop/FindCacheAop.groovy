package com.cai.cache.aop

import com.cai.cache.annotation.FindCache
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

import static com.cai.cache.util.AopCacheUtils.*

@Aspect
@Component
class FindCacheAop {

    Logger log = LoggerFactory.getLogger(FindCacheAop.class)

    @Autowired
    RedisService redisService

    @Autowired
    ApplicationContext ac

    @Autowired
    JdbcTemplate jdbcTemplate

    @Around(value = "@annotation(cacheValue)")
    Object doAroundFindCache(ProceedingJoinPoint pjp, FindCache cacheValue){
        try{
            if (!cacheValue || !BaseEntity.isAssignableFrom(cacheValue.targetClass()) || !cacheValue.targetClass().DEFINE.cache){
                return pjp.proceed(pjp.getArgs())
            }
            Class toClazz = cacheValue.targetClass()
            BaseEntity cacheEntity, entity
            List<Object> cacheObjs
            List<Map> validateMaps
            String cacheKey = getCacheName(pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(),  pjp.getArgs())
            Object res = redisService.tryAndGetOpJedis{it->
                cacheObjs = getCache(cacheValue.targetClass(), it, pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), true,  pjp.getArgs()) as List
                if (!cacheObjs || cacheObjs.empty) return null
                validateMaps = executeSqlData(jdbcTemplate, cacheValue.validateSql(), pjp.getArgs().toList(), cacheValue.param().split("\\,")?.toList())
                if (!validateMaps && validateMaps.empty && validateMaps.size() != cacheObjs.size())
                    return null
                //验证entity有效性
                cacheEntity = cacheObjs[0] as BaseEntity
                if (!cacheEntity ||
                        !cacheEntity.DEFINE ||
                        !cacheEntity.getEntityId())
                    return null
                def status = cacheObjs.stream().anyMatch {cacheObj->
                    def validate = validateMaps.find { it.id == cacheObj.id && it.lastUpdated == cacheObj.lastUpdated}
                    if (!validate) return false else return true//不存在id 代表数据有更新，淘汰缓存数据
                }
                if (!status){
                    //删除过期缓存
                    delCache(it, pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName(), pjp.getArgs())
                    return null
                }

                //----- cacheObjs 有效
                return cacheObjs
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
