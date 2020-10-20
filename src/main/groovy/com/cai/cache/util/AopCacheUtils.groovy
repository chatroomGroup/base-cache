package com.cai.cache.util


import com.alibaba.fastjson.JSONObject
import com.cai.jdbc.mysql.JdbcTemplate
import com.cai.redis.RedisService
import org.springframework.jdbc.core.BeanPropertyRowMapper
import redis.clients.jedis.Jedis

import static com.cai.redis.RedisService.serialize

class AopCacheUtils {

    final static String OK = "ok"

    static def <T> Object getCache(Class<T> clazz, Jedis jedis, String className, String simpleMethodName, boolean isCollection = false, Object... param){
        if (isCollection){
            return AopCacheUtils.unSerializeList(jedis.get(getCacheName(className, simpleMethodName, param)), clazz)
        }else{
            return AopCacheUtils.unSerialize(jedis.get(getCacheName(className, simpleMethodName, param)), clazz)
        }
    }

    static String getCacheName(String className, String simpleMethodName, Object... param){
        return "$className:$simpleMethodName:${param.join("|")}"
    }

    static boolean setCache(Object obj, Jedis jedis, String className, String simpleMethodName, Object... param){
        String res = jedis.set(getCacheName(className, simpleMethodName, param), serialize(obj))
        return res == OK
    }

    static boolean delCache(Jedis jedis, String className, String simpleMethodName, Object... param){
        String res = jedis.del(getCacheName(className, simpleMethodName, param))
        return res == OK
    }

    static def <T> T unSerialize(String cache, Class<T> clazz){
        if (!cache)
            return null
        return RedisService.unSerialize(cache?.replace("'","\""), clazz)
    }

    static def <T> T unSerializeList(String cache, Class<T> clazz){
        if (!cache)
            return null
        return RedisService.unSerialize(cache?.replace("'","\""), List)?.collect {it->
            JSONObject.toJavaObject(it, clazz)
        }
    }

    static List<Map> executeSqlData(JdbcTemplate jdbcTemplate, String sql, List<Object> args, List<String> subscripts){
        if (!subscripts || subscripts.empty){
            return jdbcTemplate.queryForList(sql)
        }
        else{
            args = buildArgs(subscripts, args).toList()
            args = args.reverse()
            while (sql.contains("?") && !args.empty){
                sql = sql.replaceFirst("[?]", args.pop() as String)
            }
            return jdbcTemplate.queryForList(sql)
        }
    }

    static def <T> List<T> executeSqlData(JdbcTemplate jdbcTemplate, String sql, List<Object> args, List<String> subscripts, Class<T> clazz){
        if (!subscripts || subscripts.empty){
            return jdbcTemplate.queryForList(sql, clazz)
        }
        else{
            args = buildArgs(subscripts, args).toList()
            args.reverse()
            while (sql.contains("?") && !args.empty){
                sql = sql.replaceFirst("[?]", args.pop() as String)
            }
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper(clazz))
        }
    }


    static Object[] buildArgs(List<String> origins, List<Object> args){
        def callArg = {Object origin ->
            origin = args[origin as Integer]
            if (origin instanceof Collection)
                return "'${origin.join("','")}'"
            else
                return "'$origin'"
        }
        return origins?.collect{ callArg(it) }?.toArray()
    }
}
