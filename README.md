# 1CourseElect

> 同济大学一站式服务平台（1.tongji.edu.cn）自动选课辅助工具

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Maven-red?logo=apache-maven)](pom.xml)

## 📖 简介

**1CourseElect** 是一款面向同济大学学生的桌面端选课辅助工具，通过模拟登录同济大学一站式服务平台，自动完成选课请求的发送，帮助学生在选课高峰期快速抢占课位。

程序基于 Java Swing 构建 GUI，支持手动/自动循环发送选课请求，并在界面内实时展示日志输出。

---

## ✨ 功能特性

- 🔐 **SSO 登录**：模拟登录同济大学一站式服务平台，支持账号密码认证（含 RSA 加密）
- 📅 **选课轮次选择**：登录后可选择当前可用的选课轮次
- 📋 **课程浏览与管理**：查询可选课程列表，添加/移除待选 / 待退课程
- 🚀 **自动发送选课请求**：支持设置发送次数与间隔，循环发送选课请求
- 📄 **实时日志**：界面内嵌日志面板，实时展示选课请求状态与结果
- 💾 **登录缓存**：支持保存登录凭据缓存，免去重复输入

---

## 🛠️ 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Java | 25 | 主要编程语言 |
| [FlatLaf](https://github.com/JFormDesigner/FlatLaf) | 3.7 | 现代化 Swing UI 主题（Darcula 风格） |
| [Fastjson2](https://github.com/alibaba/fastjson2) | 2.0.61 | JSON 序列化 / 反序列化 |
| [Lombok](https://projectlombok.org/) | 1.18.42 | 简化 Java 样板代码 |
| [Log4j2](https://logging.apache.org/log4j/2.x/) | 2.25.3 | 日志框架 |
| Maven | — | 项目构建与依赖管理 |

---

## 📁 项目结构

```
src/main/java/xyz/zcraft/
├── Main.java                   # 程序入口
├── User.java                   # 用户信息模型
├── elect/
│   ├── Course.java             # 课程模型
│   ├── CourseData.java         # 课程数据记录
│   ├── ElectRequest.java       # 选课请求模型
│   ├── ElectResult.java        # 选课结果模型
│   ├── Round.java              # 选课轮次
│   ├── RoundData.java          # 轮次数据
│   └── TeachClass.java         # 教学班模型
├── forms/
│   ├── Login.java              # 登录窗口
│   ├── CourseElect.java        # 课程选择窗口
│   └── ElectRequester.java     # 选课请求发送窗口
└── util/
    ├── AsyncHelper.java        # 异步任务工具
    ├── NetworkHelper.java      # 网络请求工具（登录、选课 API）
    ├── RSAUtil.java            # RSA 加密工具
    └── JTextAreaAppender.java  # Log4j2 → Swing 文本框日志输出
```

---

## 🚀 快速开始

### 环境要求

- **JDK 25+**
- **Maven 3.8+**

### 构建与运行

```bash
# 克隆仓库
git clone https://github.com/ZayrexDev/1CourseElect.git
cd 1CourseElect

# 编译并打包
mvn clean package

# 运行
java -jar target/1CourseElect-1.0-SNAPSHOT.jar
```

### 使用流程

1. 启动程序后，在登录界面输入同济大学一站式平台账号与密码
2. 登录成功后，选择目标选课轮次
3. 在课程选择界面搜索并添加想要选取的课程至「待选」列表
4. 前往「选课请求」界面，设置发送次数与间隔时间
5. 点击「开始」，程序将自动循环发送选课请求，并在日志面板实时展示结果

---

## ⚠️ 免责声明

本项目仅供学习与技术研究使用，请遵守同济大学相关规定，合理使用本工具。**过度频繁地发送请求可能对服务器造成负担，请自行承担使用风险。** 本项目作者不对任何由此引起的后果负责。

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

Copyright (c) 2026 Zayrex