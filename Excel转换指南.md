# 合同评审意见列示.xlsx 转换为 rules.xlsx 格式指南

## Excel规则文件格式说明

根据系统现有的 `rules.xlsx` 格式，目标文件应包含以下8列：

| 列号 | 字段名 | 说明 | 示例值 |
|------|--------|------|--------|
| 0 | contract_types | 适用的合同类型（分号分隔） | `采购;外包;NDA` |
| 1 | party_scope | 适用范围 | `Neutral` / `A` / `B` |
| 2 | risk | 风险等级 | `high` / `medium` / `low` / `blocker` |
| 3 | keywords | 关键字列表（分号分隔） | `付款方式;支付周期;付款条件` |
| 4 | regex | 正则表达式（精准匹配） | `支付.*\d+天` |
| 5 | checklist | 检查清单（换行分隔） | `1. 确认付款方式\n2. 明确付款周期` |
| 6 | suggest_A | 对甲方的建议 | `建议明确指定付款方式` |
| 7 | suggest_B | 对乙方的建议 | `建议要求提高付款保障` |

## 转换步骤

### 1. 分析原文件结构
首先需要分析 `合同评审意见列示.xlsx` 的列名和数据结构：

```python
import pandas as pd

# 读取原文件
df = pd.read_excel('合同评审意见列示.xlsx')
print("列名:", df.columns.tolist())
print("数据形状:", df.shape)
print("前3行数据:")
print(df.head(3))
```

### 2. 列名映射
根据原文件的列名，映射到目标格式：

```python
# 智能映射示例
column_mapping = {
    'contract_types': '合同类型',  # 或包含"合同"、"类型"的列
    'party_scope': '适用范围',    # 或包含"立场"、"范围"的列
    'risk': '风险等级',          # 或包含"风险"、"等级"的列
    'keywords': '关键词',        # 或包含"关键词"、"关键字"的列
    'regex': '正则表达式',        # 或包含"正则"、"表达式"的列
    'checklist': '检查清单',      # 或包含"检查"、"清单"的列
    'suggest_A': '甲方建议',      # 或包含"甲方"、"A方"的列
    'suggest_B': '乙方建议'       # 或包含"乙方"、"B方"的列
}
```

### 3. 数据转换
将原数据转换为目标格式：

```python
def convert_to_rules_format(df):
    target_columns = [
        'contract_types', 'party_scope', 'risk', 'keywords',
        'regex', 'checklist', 'suggest_A', 'suggest_B'
    ]
    
    rules_df = pd.DataFrame(columns=target_columns)
    
    for index, row in df.iterrows():
        new_row = {}
        
        # 根据映射填充数据
        for target_col, source_col in column_mapping.items():
            if source_col in df.columns:
                value = row[source_col]
                new_row[target_col] = str(value) if pd.notna(value) else ""
            else:
                new_row[target_col] = ""
        
        # 设置默认值
        if 'contract_types' not in new_row or not new_row['contract_types']:
            new_row['contract_types'] = "通用合同"
        if 'party_scope' not in new_row or not new_row['party_scope']:
            new_row['party_scope'] = "Neutral"
        if 'risk' not in new_row or not new_row['risk']:
            new_row['risk'] = "medium"
        
        rules_df = pd.concat([rules_df, pd.DataFrame([new_row])], ignore_index=True)
    
    return rules_df
```

### 4. 保存转换后的文件
```python
# 保存为rules.xlsx格式
rules_df.to_excel('src/main/resources/review-rules/rules_converted.xlsx', 
                  index=False, engine='openpyxl')
```

## 关键信息保留策略

### 1. 合同类型映射
- 原文件中的合同类型 → `contract_types` 列
- 支持多个类型用分号分隔：`采购;外包;NDA`

### 2. 风险等级标准化
- 高风险 → `high`
- 中风险 → `medium`  
- 低风险 → `low`
- 阻塞性风险 → `blocker`

### 3. 关键字提取
- 从原文件的评审意见中提取关键词
- 用分号分隔多个关键词：`付款方式;支付周期;付款条件`

### 4. 检查清单格式化
- 将评审要点转换为检查清单
- 使用换行符分隔：`1. 确认付款方式\n2. 明确付款周期`

### 5. 建议分类
- 针对甲方的建议 → `suggest_A`
- 针对乙方的建议 → `suggest_B`
- 通用建议可以同时填入两列

## 使用转换后的规则

转换完成后，新的规则文件将：

1. **自动加载**：系统启动时自动加载 `rules_converted.xlsx`
2. **规则匹配**：在合同审查时进行关键字和正则匹配
3. **Prompt生成**：为LLM生成包含检查清单和建议的Prompt
4. **批注定位**：支持精确的文字级批注定位

## 验证转换结果

转换完成后，可以通过以下方式验证：

1. **检查文件格式**：确保8列格式正确
2. **测试规则匹配**：使用测试合同验证规则匹配效果
3. **验证Prompt生成**：检查生成的Prompt是否包含正确信息
4. **测试批注功能**：验证批注定位是否准确
