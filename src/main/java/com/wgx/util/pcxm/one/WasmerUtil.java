package com.wgx.util.pcxm.one;

import org.wasmer.Instance;
import org.wasmer.Module;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 解析WebAssembly工具类(wasmer-java方案)
 * <p>
 * 注意: 必须全局共享同一个Instance!
 * 之前报 out of bounds memory access 的原因就是每个方法里都重新instantiate了一个新实例,
 * 数据写进A实例的线性内存,调g函数时却在B实例上执行,内存根本不共享。
 *
 * @author wgx
 * @date 2024/8/31
 */
public class WasmerUtil {

    /**
     * 全局唯一的wasm实例,decrypt.wasm在resources目录下
     */
    private static volatile Instance instance;

    private static Instance getInstance() {
        if (instance == null) {
            synchronized (WasmerUtil.class) {
                if (instance == null) {
                    try (InputStream is = WasmerUtil.class.getClassLoader().getResourceAsStream("decrypt.wasm")) {
                        if (is == null) {
                            throw new RuntimeException("resources目录下找不到decrypt.wasm");
                        }
                        byte[] wasmBytes = is.readAllBytes();
                        instance = new Module(wasmBytes).instantiate();
                    } catch (IOException e) {
                        throw new RuntimeException("读取decrypt.wasm失败", e);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * WebAssembly里面的a方法,获取栈指针
     */
    public static int a(int number) {
        Object[] results = getInstance().exports.getFunction("a").apply(number);
        return (Integer) results[0];
    }

    /**
     * WebAssembly里面的c方法,内存分配(相当于malloc),内存不够时会自动扩容
     */
    public static int c(int length) {
        Object[] results = getInstance().exports.getFunction("c").apply(length);
        return (Integer) results[0];
    }

    /**
     * WebAssembly里面的获取内存方法,对应python的exports.i
     * 因为c函数可能触发扩容,所以每次都重新获取buffer,不要缓存
     */
    public static ByteBuffer i() {
        return getInstance().exports.getMemory("i").buffer();
    }

    /**
     * WebAssembly里面的g方法,对应js里的window.xmDecrypt
     */
    public static void g(int stackPointer, int dataOffset, int dataLength, int trackIdOffset, int trackIdLength) {
        getInstance().exports.getFunction("g").apply(stackPointer, dataOffset, dataLength, trackIdOffset, trackIdLength);
    }

}
