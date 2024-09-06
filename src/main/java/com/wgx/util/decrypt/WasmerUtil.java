package com.wgx.util.decrypt;

import org.wasmer.Instance;
import org.wasmer.Memory;
import org.wasmer.Module;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 解析WebAssembly工具类
 *
 * @author wgx
 * @date 2024/8/31
 */
public class WasmerUtil {

    /**
     * 获取实例
     */
    private static Instance getInstance() {
        // `simple.wasm` is located at `tests/resources/`.
        Path wasmPath = Paths.get("D:/workplaces/git/xmly-download/target/classes/decrypt.wasm");

        //以字节形式读取WebAssembly模块。 Reads the WebAssembly module as bytes.
        byte[] wasmBytes = new byte[0];
        try {
            wasmBytes = Files.readAllBytes(wasmPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Module module = new Module(wasmBytes);
        // 实例化WebAssembly模块 Instantiates the WebAssembly module.
        return module.instantiate();
    }

    /**
     * WebAssembly里面的a方法
     */
    public static Integer a(Integer number) {
        Instance instance = getInstance();
        //调用导出的函数，并返回一个对象数组。 Calls an exported function, and returns an object array.
        Object[] results = instance.exports.getFunction("a").apply(number);
        instance.close();
        return (Integer) results[0];
    }

    /**
     * WebAssembly里面的c方法
     */
    public static Integer c(int length) {
        Instance instance = getInstance();
        //调用导出的函数，并返回一个对象数组。 Calls an exported function, and returns an object array.
        Object[] results = instance.exports.getFunction("c").apply(length);
        instance.close();
        return (Integer) results[0];
    }

    /**
     * WebAssembly里面的获取内存方法
     */
    public static ByteBuffer i() {
        Instance instance = getInstance();
        instance.close();
        // 动态扩展内存
        Memory memory = instance.exports.getMemory("i");
      int delta = 10000;
        int result = memory.grow(delta);
        System.out.println("扩容结果:"+result);
        return memory.buffer();
    }

    /**
     * WebAssembly里面的g方法
     */
    public static void g(Integer stackPointer, Integer dataOffset, int dataLength, Integer trackIdOffset, int trackIdLength) {
        Instance instance = getInstance();
        System.out.println("gmemory:"+instance.exports.getMemory("i").buffer().capacity());
        instance.exports.getFunction("g").apply(stackPointer, dataOffset, dataLength, trackIdOffset, trackIdLength);
       // instance.close();
    }
}
