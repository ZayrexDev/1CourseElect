# 1CourseElect

> 同济大学一站式服务平台（1.tongji.edu.cn）自动选课辅助工具

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Maven-red?logo=apache-maven)](pom.xml)

## 📖 简介

**1CourseElect** 是一款面向同济大学学生的桌面端选课辅助工具，通过模拟登录同济大学一站式服务平台，自动完成选课请求的发送，帮助学生在选课高峰期快速抢占课位。

程序基于 Java Swing 构建 GUI，支持手动/自动循环发送选课请求，并在界面内实时展示日志输出。

---

## ⚠️ 免责声明

本项目仅供学习与技术研究使用，请遵守同济大学相关规定，合理使用本工具代码。本项目作者不对任何因使用此程序及其代码、二次开发版本等所引起的后果负责。

---

## 🖼️ 截图

<img width="1104" height="785" alt="image" src="https://github.com/user-attachments/assets/e10524b1-f5a2-41b2-a806-2689f36f1cb2" />

---

## ✨ 功能特性

- 🔐 **SSO 登录**：模拟登录同济大学一站式服务平台，支持账号密码认证（含 RSA 加密）
- 📅 **选课轮次选择**：登录后可选择当前可用的选课轮次
- 📋 **课程浏览与管理**：查询可选课程列表，添加/移除待选 / 待退课程
- 🚀 **自动发送选课请求**：支持手动、检测到有空容量或到达特定时间发送选课请求
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
4. 在「发送请求」界面，设置发送参数
5. 手动点击「发送」或待条件满足（检测到空容、到特定时间）时，程序将发送选课请求，并在日志面板实时展示结果

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

Copyright (c) 2026 Zayrex
