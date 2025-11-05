#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简化版Excel转换脚本
手动分析合同评审意见列示.xlsx并转换为rules.xlsx格式
"""

import pandas as pd
import os

def main():
    """主函数"""
    input_file = "合同评审意见列示.xlsx"
    
    print("=== 合同评审意见列示.xlsx 分析工具 ===")
    
    try:
        # 读取Excel文件
        print("正在读取Excel文件...")
        df = pd.read_excel(input_file)
        
        print(f"✓ 成功读取文件")
        print(f"数据形状: {df.shape}")
        print(f"列名: {df.columns.tolist()}")
        
        print("\n前5行数据:")
        for i in range(min(5, len(df))):
            print(f"\n第{i+1}行:")
            for col in df.columns:
                value = df.iloc[i][col]
                print(f"  {col}: {value}")
        
        print("\n数据类型:")
        print(df.dtypes)
        
        print("\n非空值统计:")
        print(df.count())
        
        # 分析列名，提供映射建议
        print("\n=== 列名映射建议 ===")
        original_columns = df.columns.tolist()
        
        mapping_suggestions = {
            'contract_types': '合同类型',
            'party_scope': '适用范围/立场',
            'risk': '风险等级',
            'keywords': '关键词/关键字',
            'regex': '正则表达式',
            'checklist': '检查清单',
            'suggest_A': '甲方建议',
            'suggest_B': '乙方建议'
        }
        
        for target_col, description in mapping_suggestions.items():
            print(f"{target_col} ({description}):")
            for col in original_columns:
                if any(keyword in str(col).lower() for keyword in description.lower().split('/')):
                    print(f"  -> 建议映射到: {col}")
            print()
        
    except Exception as e:
        print(f"✗ 处理失败: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
