## Purpose

让学生通过低门槛填写或上传创建简历，并由 Mentor 在证据支持下持续维护可追溯的简历版本。

## ADDED Requirements

### Requirement: 创建和上传简历
系统 SHALL 支持用户在线创建简历或上传 PDF、Word 简历；在线简历只填写姓名和学校即可提交。

#### Scenario: 上传简历
- **WHEN** 用户上传已有简历
- **THEN** 系统保留原始文件并提供可继续编辑的简历版本

### Requirement: 自动更新和版本
Mentor SHALL 根据学习或面试中的有效能力证据自动更新可编辑简历，在主对话告知改动并保留历史版本供查看和恢复。

#### Scenario: 练习产生新能力
- **WHEN** 用户通过练习验证一项技能但尚未在真实项目中使用
- **THEN** Mentor 可将其写入技能或学习成果，不将其编造成项目经历

#### Scenario: 查看更新前版本
- **WHEN** Mentor 自动更新简历
- **THEN** 用户可以查看并恢复此前的版本
