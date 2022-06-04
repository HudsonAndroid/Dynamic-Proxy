package com.hudson.dynamic_proxy

import com.google.common.reflect.TypeToken
import com.hudson.dynamic_proxy.compiler.compile
import com.hudson.dynamic_proxy.handler.InvocationHandler
import com.squareup.javapoet.*
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.net.URL
import java.net.URLClassLoader
import javax.lang.model.element.Modifier

/**
 * Created by Hudson on 2022/6/4.
 */
object Proxy {
    /**
     * 代理类文件的根路径（去除包名后的）
     */
    const val PKG_ROOT_PATH = "./app/src/main/kotlin/"

    /**
     * 默认代码生成的class文件的位置
     *
     * 可以通过查看对应App.kt对应的位置找到确定的目录
     *
     * 仅在选择类加载方式为复制class文件到build目录时生效
     */
    const val BUILD_CLASS_ROOT = "./app/build/classes/kotlin/main/"

    /**
     * 自定义ClassLoader情况下，搜寻class文件的根路径
     *
     * 注意跟文件系统相关，定位到工程根目录即可
     *
     * 仅在选择类加载方式为自定义ClassLoader加载时生效
     */
    const val CLASS_LOADER_FIND_ROOT = "F:/projects/Dynamic-Proxy/"

    private fun packageName(clazz: Class<*>) = clazz.`package`.name

    private fun proxyFileName(clazz: Class<*>) = "${clazz.simpleName}Proxy"

    fun newProxyInstance(realSubjectClazz: Class<*>, handler: InvocationHandler): Any {
        val typeBuilder = TypeSpec.classBuilder(proxyFileName(realSubjectClazz))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(
                // 不检查警告
                AnnotationSpec
                    .builder(SuppressWarnings::class.java)
                    .addMember("value", "\$S","unchecked")
                    .build()
            )

        val interfaces = if(realSubjectClazz.isInterface){
            // 如果本身就是接口类型 (暂未考虑接口本身继承自其他接口)
            arrayOf(realSubjectClazz)
        }else{
            realSubjectClazz.interfaces
        }

        // 逐个添加所实现的接口类型
        interfaces.forEach {
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
        interfaces.forEach {
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
            writeTo(File(PKG_ROOT_PATH))
        }

        // 编译，加载，并实例化一个对象
        return ProxyLoader().newProxyInstance(realSubjectClazz, handler)
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

    class ProxyLoader {

        fun newProxyInstance(clazz: Class<*>, handler: InvocationHandler): Any {
            // 编译
            compile(File(getProxyFilePath(clazz)))

            // 加载，并创建实例
            return loadProxyClass(clazz)
                .getConstructor(InvocationHandler::class.java)
                .newInstance(handler)
        }

        private fun loadProxyClass(clazz: Class<*>, copyToBuildDir: Boolean = true): Class<*> {
            // 注意：当应用程序编译运行后，实际上会在app/build/classes目录中生成对应的class文件
            // 而ClassLoader会在这里面查找并加载相关类
            // 由于我们的生成类是经过我们编译生成的class文件，因此应用自身的ClassLoader是无法加载到我们
            // 产出的class的。 有两种方案：
            // 1.将class编译结果路径也放到build目录下
            // 2.新建一个ClassLoader手动加载我们的class文件

            // 方式一：将产生的class文件复制到build目录下
            val packageName = packageName(clazz)
            val proxyFileName = proxyFileName(clazz)
            return if(copyToBuildDir){
                val pkgPath = packageName.replace(".", "/")
                File(getProxyFilePath(clazz).replace(".java", ".class")).copyTo(
                    File("${BUILD_CLASS_ROOT}$pkgPath/$proxyFileName.class"),
                    true
                )
                Class.forName(
                    "$packageName.$proxyFileName",
                    false, App::class.java.classLoader)
            }else{
                // 方式二：新建ClassLoader并设置搜索路径
                val clazzLoadPath = "${CLASS_LOADER_FIND_ROOT}${PKG_ROOT_PATH}"
                val classLoader = URLClassLoader(arrayOf(URL("file:/$clazzLoadPath")))
                classLoader.loadClass("$packageName.$proxyFileName")
            }
        }

        private fun getProxyFilePath(clazz: Class<*>): String {
            val pathBuilder = StringBuilder(PKG_ROOT_PATH)
                .append(packageName(clazz).replace(".", "/"))
                .append("/")
                .append(proxyFileName(clazz))
                .append(".java")
            return pathBuilder.toString()
        }
    }
}
