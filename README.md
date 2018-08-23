# FileSync
将本地多个文件夹的内容实时同步到另外一个文件夹中

## 使用
```
java -jar FileSync-v1.0.jar
```

## 目录配置
```
# 监视目录。
watchPath=D\:\\a\\

# 同步目标目录。
syncPath=D\:\\b\\

# 差异判断周期，单位秒。
# 默认 2 秒。
watchInterval=2

# 排除的文件类型。
# 如：log,project
excludeExts=project,log,gitignore
```

或使用 excludeFiles.txt 目录来指定要排除的文件或文件夹。