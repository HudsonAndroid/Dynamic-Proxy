package com.hudson.dynamic_proxy.real

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
}