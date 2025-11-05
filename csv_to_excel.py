#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将CSV格式的规则文件转换为Excel格式
"""

import pandas as pd
import os

def convert_csv_to_excel():
    """将CSV转换为Excel格式"""
    
    csv_file = "rules_converted.csv"
    excel_file = "src/main/resources/review-rules/rules_converted.xlsx"
    
    try:
        # 读取CSV文件
        print(f"正在读取CSV文件: {csv_file}")
        df = pd.read_csv(csv_file)
        
        print(f"✓ 成功读取CSV文件")
        print(f"数据形状: {df.shape}")
        print(f"列名: {df.columns.tolist()}")
        
        # 确保输出目录存在
        os.makedirs(os.path.dirname(excel_file), exist_ok=True)
        
        # 保存为Excel文件
        df.to_excel(excel_file, index=False, engine='openpyxl')
        
        print(f"✓ 成功转换为Excel格式: {excel_file}")
        
        # 显示转换结果
        print(f"\n转换结果:")
        print(f"数据形状: {df.shape}")
        print(f"列名: {df.columns.tolist()}")
        
        print("\n前3行数据:")
        print(df.head(3))
        
        return True
        
    except Exception as e:
        print(f"✗ 转换失败: {e}")
        return False

if __name__ == "__main__":
    success = convert_csv_to_excel()
    
    if success:
        print(f"\n✅ 转换完成！")
        print(f"CSV文件: rules_converted.csv")
        print(f"Excel文件: src/main/resources/review-rules/rules_converted.xlsx")
        print(f"\n使用说明:")
        print(f"1. 转换后的Excel文件已保存到系统规则目录")
        print(f"2. 系统会自动加载新的规则文件")
        print(f"3. 可以在合同审查时使用这些规则")
        print(f"4. 支持关键字匹配和正则表达式匹配")
    else:
        print(f"\n❌ 转换失败！")
