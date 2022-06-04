package com.hudson.dynamic_proxy

import com.google.common.reflect.TypeToken
import com.hudson.dynamic_proxy.handler.InvocationHandler
import com.squareup.javapoet.*
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Type
import javax.lang.model.element.Modifier

/**
 * Created by Hudson on 2022/6/4.
 */
object Proxy {

    fun packageName(clazz: Class<*>) = clazz.`package`.name

    fun proxyFileName(clazz: Class<*>) = "${clazz.simpleName}Proxy"

    fun newProxyInstance(realSubjectClazz: Class<*>, handler: InvocationHandler){
        val typeBuilder = TypeSpec.classBuilder(proxyFileName(realSubjectClazz))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
                // 不检查警告
                AnnotationSpec
                    .builder(SuppressWarnings::class.java)
                    .addMember("value", "\$S","unchecked")
                    .build()
            )

        // 逐个添加所实现的接口类型
        realSubjectClazz.interfaces.forEach {
            typeBuilder.addSuperinterface(it)
        }

        // 创建成员变量，即真实类实例
        val fieldSpec = FieldSpec.builder(
            InvocationHandler::class.java,
            "handler",
            Modifier.PRIVATE
        ).build()

        typeBuilder.addField(fieldSpec)

        // 创建需要传入ApiService实例的构造方法
        val constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(InvocationHandler::class.java, "handler") // 添加参数
            .addStatement("this.handler = handler")
            .build()

        typeBuilder.addMethod(constructor)

        // 考虑到realSubject可能实现多个接口，因此遍历接口
        realSubjectClazz.interfaces.forEach {
            // 找到所有的方法
            for(method in it.declaredMethods){
                /**
                 *  @Override
                    public List getBanner() {
                        try {
                            Method method = com.hudson.dynamic_proxy.real.ApiService.class.getMethod("getBanner");
                            return (List)handler.invoke(method);
                        } catch(Exception e){
                            e.printStackTrace();
                        }
                        return null;
                    }
                 */
                val methodSpec = MethodSpec.methodBuilder(method.name)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override::class.java)
                    .returns(method.returnType)
                    .addParameters(method)
                    .addCode("try {\n")
                        // 反射找到该方法的类
                    .addReflectMethodStatement(method, it)
                    .addHandlerInvokeStatement(method)
                    .addCode("} catch(Exception e){ \n")
                    .addCode("\te.printStackTrace();\n")
                    .addCode("}\n")
                    .addDefaultReturn(method.returnType)
                    .build()

                typeBuilder.addMethod(methodSpec)
            }
        }

        JavaFile.builder(
            packageName(realSubjectClazz),
            typeBuilder.build()
        ).build().apply {
            writeTo(File(App.PKG_ROOT_PATH))
        }
    }

    private fun MethodSpec.Builder.addParameters(method: Method): MethodSpec.Builder {
        val parameters = method.parameters
        val parameterTypes = method.parameterTypes
        parameterTypes.forEachIndexed { index, clazz ->
            addParameter(ParameterSpec.builder(clazz, parameters[index].name).build())
        }
        return this
    }

    private fun isVoidReturnType(returnType: Class<*>) = returnType == Void.TYPE

    private fun MethodSpec.Builder.addDefaultReturn(returnType: Class<*>): MethodSpec.Builder {
        if(isVoidReturnType(returnType)) return this
        val defaultValue =  when(returnType){
            Boolean::class.java -> "false"
            Character::class.java -> "0"
            Byte::class.java -> "0"
            Short::class.java -> "0"
            Integer::class.java -> "0"
            Long::class.java -> "0L"
            Float::class.java -> "0F"
            Double::class.java -> "0.0"
            else -> "null"
        }
        addCode("return $defaultValue;\n")
        return this
    }

    /**
     * 对应的生成代码为
     *  Method method = ApiService.class.getMethod("xxx", Class[]{A.class, B.class ... });
     */
    private fun MethodSpec.Builder.addReflectMethodStatement(method: Method, interfaceClazz: Class<*>): MethodSpec.Builder {
        val typeArgs = mutableListOf<Type>(Method::class.java)
        val parameterTypes = method.parameterTypes
        val formatSb = if(parameterTypes.isNotEmpty()){
            //https://stackoverflow.com/questions/46401104/only-classes-are-allowed-on-the-left-hand-side-of-a-class-literal
//            typeArgs.add(Array<Class<Any>>::class.java)
            val type: Type = object: TypeToken<Array<Class<*>>>(){}.type
            typeArgs.add(type)
            StringBuilder(",new \$T{")
        }else{
            StringBuilder("")
        }
        parameterTypes.forEachIndexed { index, it ->
            typeArgs.add(it)
            formatSb.append("\$T.class")
            if(index != parameterTypes.lastIndex){
                formatSb.append(",")
            }
        }
        if(parameterTypes.isNotEmpty()){
            formatSb.append("}")
        }
        val prefix =
            "\t\$T method = ${interfaceClazz.name}.class.getMethod(\"${method.name}\"${formatSb})"

        // kotlin将array转为 Java varargs：   在数组前面加个*
        // https://stackoverflow.com/questions/45854994/convert-kotlin-array-to-java-varargs
        addStatement(prefix, *typeArgs.toTypedArray())
        return this
    }

    /**
     * 对应的生成代码为
     *  handler.invoke(method, a, b, c, ...)
     */
    private fun MethodSpec.Builder.addHandlerInvokeStatement(method: Method): MethodSpec.Builder {
        val parameters = method.parameters
        val formatSb = if(parameters.isNotEmpty()){
            StringBuilder(",")
        }else{
            StringBuilder("")
        }
        parameters.forEachIndexed { index, it ->
            formatSb.append(it.name)
            if(index != parameters.lastIndex){
                formatSb.append(",")
            }
        }
        if(isVoidReturnType(method.returnType)){
            addStatement("\thandler.invoke(method$formatSb)")
        }else{
            addStatement("\treturn (\$T)handler.invoke(method$formatSb)", method.returnType)
        }

        return this
    }
}