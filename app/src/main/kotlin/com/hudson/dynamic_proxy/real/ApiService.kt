package com.hudson.dynamic_proxy.real

import com.hudson.dynamic_proxy.Param

/**
 * Created by Hudson on 2022/6/4.
 */
interface ApiService {

    fun getBanner(): List<String>

    fun login(): Boolean

    fun voidReturn()

    fun withParam(hello: String): String

    fun withCustomParam(param: Param): Param
}