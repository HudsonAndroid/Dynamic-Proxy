package com.hudson.dynamic_proxy.real

import com.hudson.dynamic_proxy.Param

/**
 * Created by Hudson on 2022/6/4.
 */
class RealApiService: ApiService {
    override fun getBanner(): List<String> {
        return listOf("banner")
    }

    override fun login(): Boolean {
        return true
    }

    override fun voidReturn() {

    }

    override fun withParam(hello: String): String {
        return "$hello hudson"
    }

    override fun withCustomParam(param: Param): Param {
        return param
    }
}