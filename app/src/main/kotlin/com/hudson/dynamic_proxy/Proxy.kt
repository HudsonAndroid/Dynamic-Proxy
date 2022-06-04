package com.hudson.dynamic_proxy

import com.hudson.dynamic_proxy.real.RealApiService
import com.squareup.javapoet.*
import java.io.File
import javax.lang.model.element.Modifier

/**
 * Created by Hudson on 2022/6/4.
 */
object Proxy {

    fun packageName(clazz: Class<*>) = clazz.`package`.name

    fun proxyFileName(clazz: Class<*>) = "${clazz.simpleName}Proxy"

    fun newProxyInstance(realApiService: RealApiService, interfaces: Class<*>){
        val typeBuilder = TypeSpec.classBuilder(proxyFileName(realApiService.javaClass))
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(interfaces)
            .addAnnotation(
                // 不检查警告
                AnnotationSpec
                    .builder(SuppressWarnings::class.java)
                    .addMember("value", "\$S","unchecked")
                    .build()
            )

        // 创建成员变量，即真实类实例
        val fieldSpec = FieldSpec.builder(
            realApiService.javaClass,
            "realSubject",
            Modifier.PRIVATE
        ).build()

        typeBuilder.addField(fieldSpec)

        // 创建需要传入ApiService实例的构造方法
        val constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(realApiService.javaClass, "realSubject") // 添加参数
            .addStatement("this.realSubject = realSubject")
            .build()

        typeBuilder.addMethod(constructor)

        // 找到所有的方法
        for(method in interfaces.declaredMethods){
            val methodSpec = MethodSpec.methodBuilder(method.name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(method.returnType)
                .addStatement("return realSubject.${method.name}()")
                .build()

            typeBuilder.addMethod(methodSpec)
        }

        JavaFile.builder(
            packageName(interfaces),
            typeBuilder.build()
        ).build().apply {
            writeTo(File(App.PKG_ROOT_PATH))
        }
    }
}