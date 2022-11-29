# HuanXinKeFuEasyUiLib ![最新版本](https://img.shields.io/github/v/release/jackyHuangH/HuanXinKeFuEasyUiLib)
环信客服SDK二次封装，基于huanxinkefusdk-1.2.5, glide-4.13.1

# 项目配置

```
  allprojects {
      repositories {
          ...
          maven { url 'https://jitpack.io' }  //添加jitpack仓库
      }
  }
  
  dependencies {
       //使用androidx，support版本用户请尽快适配Androidx
       //2个依赖都必须添加！！！
      implementation 'com.github.jackyHuangH.HuanXinKeFuEasyUiLib:kefu-easeui:latest release'
      implementation 'com.github.jackyHuangH.HuanXinKeFuEasyUiLib:player:latest release'
  }
```
