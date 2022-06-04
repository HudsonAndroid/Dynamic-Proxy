package com.hudson.dynamic_proxy.handler

import java.lang.reflect.Method

/**
 * 方法逻辑处理类
 * Created by Hudson on 2022/6/4.
 */
interface InvocationHandler {

    fun invoke(method: Method, vararg args: Any?): Any?
}