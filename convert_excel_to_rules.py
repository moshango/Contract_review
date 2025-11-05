#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
合同评审意见列示.xlsx 转换为 rules.xlsx 格式
保留关键信息，按照系统规则文件格式改造
"""

import pandas as pd
import sys
import os

def read_excel_file(file_path):
    """读取Excel文件"""
    try:
        # 尝试不同的引擎
        for engine in ['openpyxl', 'xlrd']:
            try:
                df = pd.read_excel(file_path, engine=engine)
                print(f"✓ 使用 {engine} 引擎成功读取文件")
                return df
            except Exception as e:
                print(f"✗ {engine} 引擎失败: {e}")
                continue
        
        # 如果都失败，尝试不指定引擎
        df = pd.read_excel(file_path)
        print("✓ 使用默认引擎成功读取文件")
        return df
        
    except Exception as e:
        print(f"✗ 读取Excel文件失败: {e}")
        return None

def analyze_excel_structure(df):
    """分析Excel文件结构"""
    print("\n=== Excel文件结构分析 ===")
    print(f"数据形状: {df.shape}")
    print(f"列名: {df.columns.tolist()}")
    
    print("\n前5行数据:")
    print(df.head())
    
    print("\n数据类型:")
    print(df.dtypes)
    
    print("\n非空值统计:")
    print(df.count())
    
    return df

def convert_to_rules_format(df):
    """转换为rules.xlsx格式"""
    print("\n=== 开始转换格式 ===")
    
    # 定义目标格式的列名（按照rules.xlsx格式）
    target_columns = [
        'contract_types',    # 合同类型
        'party_scope',       # 适用范围
        'risk',             # 风险等级
        'keywords',         # 关键字
        'regex',            # 正则表达式
        'checklist',        # 检查清单
        'suggest_A',        # 对甲方的建议
        'suggest_B'         # 对乙方的建议
    ]
    
    # 创建新的DataFrame
    rules_df = pd.DataFrame(columns=target_columns)
    
    # 分析原文件的列名，尝试映射
    original_columns = df.columns.tolist()
    print(f"原文件列名: {original_columns}")
    
    # 智能映射列名
    column_mapping = {}
    
    # 尝试匹配列名
    for col in original_columns:
        col_lower = str(col).lower()
        
        if any(keyword in col_lower for keyword in ['合同', 'contract', '类型', 'type']):
            column_mapping['contract_types'] = col
        elif any(keyword in col_lower for keyword in ['立场', 'party', 'scope', '范围']):
            column_mapping['party_scope'] = col
        elif any(keyword in col_lower for keyword in ['风险', 'risk', '等级', 'level']):
            column_mapping['risk'] = col
        elif any(keyword in col_lower for keyword in ['关键词', 'keyword', '关键字']):
            column_mapping['keywords'] = col
        elif any(keyword in col_lower for keyword in ['正则', 'regex', '表达式']):
            column_mapping['regex'] = col
        elif any(keyword in col_lower for keyword in ['检查', 'checklist', '清单']):
            column_mapping['checklist'] = col
        elif any(keyword in col_lower for keyword in ['甲方', 'A方', 'suggest_a']):
            column_mapping['suggest_A'] = col
        elif any(keyword in col_lower for keyword in ['乙方', 'B方', 'suggest_b']):
            column_mapping['suggest_B'] = col
    
    print(f"列名映射: {column_mapping}")
    
    # 转换数据
    for index, row in df.iterrows():
        new_row = {}
        
        # 填充映射的列
        for target_col, source_col in column_mapping.items():
            if source_col in df.columns:
                value = row[source_col]
                if pd.notna(value):
                    new_row[target_col] = str(value)
                else:
                    new_row[target_col] = ""
            else:
                new_row[target_col] = ""
        
        # 填充未映射的列（使用默认值）
        for col in target_columns:
            if col not in new_row:
                if col == 'contract_types':
                    new_row[col] = "通用合同"  # 默认合同类型
                elif col == 'party_scope':
                    new_row[col] = "Neutral"  # 默认中立立场
                elif col == 'risk':
                    new_row[col] = "medium"  # 默认中等风险
                else:
                    new_row[col] = ""
        
        rules_df = pd.concat([rules_df, pd.DataFrame([new_row])], ignore_index=True)
    
    return rules_df

def save_rules_file(rules_df, output_path):
    """保存为rules.xlsx格式"""
    try:
        # 确保输出目录存在
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        # 保存文件
        rules_df.to_excel(output_path, index=False, engine='openpyxl')
        print(f"✓ 成功保存到: {output_path}")
        
        # 显示保存后的信息
        print(f"\n转换后的文件信息:")
        print(f"数据形状: {rules_df.shape}")
        print(f"列名: {rules_df.columns.tolist()}")
        
        print("\n前3行转换后的数据:")
        print(rules_df.head(3))
        
        return True
        
    except Exception as e:
        print(f"✗ 保存文件失败: {e}")
        return False

def main():
    """主函数"""
    input_file = "合同评审意见列示.xlsx"
    output_file = "src/main/resources/review-rules/rules_converted.xlsx"
    
    print("=== 合同评审意见列示.xlsx 转换工具 ===")
    
    # 检查输入文件是否存在
    if not os.path.exists(input_file):
        print(f"✗ 输入文件不存在: {input_file}")
        return
    
    # 读取Excel文件
    df = read_excel_file(input_file)
    if df is None:
        return
    
    # 分析文件结构
    analyze_excel_structure(df)
    
    # 转换为rules格式
    rules_df = convert_to_rules_format(df)
    
    # 保存转换后的文件
    if save_rules_file(rules_df, output_file):
        print(f"\n✅ 转换完成！")
        print(f"原文件: {input_file}")
        print(f"转换后: {output_file}")
        print(f"共转换 {len(rules_df)} 条规则")
    else:
        print(f"\n❌ 转换失败！")

if __name__ == "__main__":
    main()
