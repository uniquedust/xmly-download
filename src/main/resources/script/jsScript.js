const nodeid3 = require('node-id3');
const fs = require('fs');

//旧版本
//const buffer = fs.readFileSync('D:/1.xm'); // 读取音频文件到缓冲区
//const tags = nodeid3.getTagsFromBuffer(buffer) //使用 getTagsFromBuffer 获取音频文件的元数据标签
//高版本没有getTagsFromBuffer暴漏出来，可以直接使用read

const buffer = fs.readFileSync(process.argv[2]);
const tags = nodeid3.read(process.argv[2]);
const removeTags = nodeid3.removeTagsFromBuffer(buffer);
//计算下标签所占字节数
tags.headerSize = buffer.length-removeTags.length;

// 处理结果
console.log(JSON.stringify(tags));

// const E = nodeid3.removeTagsFromBuffer(buffer);
// console.log(E);