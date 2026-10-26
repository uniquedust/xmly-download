package com.wgx.util.pcxm.two;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import java.io.IOException;
import java.io.InputStream;

/**
 * 解析WebAssembly工具类(Chicory方案)
 * <p>
 * Chicory是纯java的wasm解释器,不依赖本地dll,但是要求JDK11+。
 * 相比wasmer-java的好处: 跨平台、无需手动install本地jar、实例内内存引用在扩容后依然有效。
 *
 * @author wgx
 * @date 2026/10/24
 */
public class WasmRuntimeUtil {

    /**
     * 全局唯一的wasm实例,线性内存必须在同一个实例内读写
     */
    private static volatile Instance instance;
    private static volatile Memory memory;

    private static Instance getInstance() {
        if (instance == null) {
            synchronized (WasmRuntimeUtil.class) {
                if (instance == null) {
                    WasmModule module = Parser.parse(readWasm());
                    instance = Instance.builder(module).build();
                    //对应python的memory_i = xm_encryptor.exports.i
                    memory = instance.exports().memory("i");
                }
            }
        }
        return instance;
    }

    private static byte[] readWasm() {
        try (InputStream is = WasmRuntimeUtil.class.getClassLoader().getResourceAsStream("decrypt.wasm")) {
            if (is == null) {
                throw new RuntimeException("resources目录下找不到decrypt.wasm");
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取decrypt.wasm失败", e);
        }
    }

    /**
     * WebAssembly里面的a方法,获取栈指针
     */
    public static int a(int number) {
        return (int) getInstance().exports().function("a").apply(number)[0];
    }

    /**
     * WebAssembly里面的c方法,内存分配(相当于malloc),内存不够时会自动扩容
     */
    public static int c(int length) {
        return (int) getInstance().exports().function("c").apply(length)[0];
    }

    /**
     * 获取线性内存,对应python的exports.i
     * Chicory的Memory对象在wasm内部扩容后引用依然有效,不用重新获取
     */
    public static Memory i() {
        getInstance();
        return memory;
    }

    /**
     * WebAssembly里面的g方法,对应js里的window.xmDecrypt
     */
    public static void g(int stackPointer, int dataOffset, int dataLength, int trackIdOffset, int trackIdLength) {
        getInstance().exports().function("g").apply(stackPointer, dataOffset, dataLength, trackIdOffset, trackIdLength);
    }

}
