package com.hudson.dynamic_proxy.real

/**
 * Created by Hudson on 2022/6/4.
 */
interface ApiService {

    fun getBanner(): List<String>

    fun login(): Boolean
}