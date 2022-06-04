package com.hudson.dynamic_proxy.compiler

import java.io.File
import javax.tools.ToolProvider

/**
 * Created by Hudson on 2022/6/4.
 */
fun compile(javaFile: File){
    ToolProvider.getSystemJavaCompiler().run {
        val fileManager = getStandardFileManager(null, null, null)
        val options = listOf("-Xlint:unchecked") // 增加lint检查日志
        getTask(null,
            fileManager,
            null,
            options,
            null,
            fileManager.getJavaFileObjects(javaFile)
        ).call()
        fileManager.close()
    }
}