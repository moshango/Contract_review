#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
合同评审意见列示.xlsx 转换为 rules.xlsx 格式
基于常见Excel结构进行智能转换
"""

import pandas as pd
import os
import re

def create_sample_rules():
    """创建示例规则数据，基于常见的合同评审意见"""
    
    # 基于常见的合同评审意见创建示例规则
    sample_rules = [
        {
            'contract_types': '通用合同',
            'party_scope': 'Neutral',
            'risk': 'high',
            'keywords': '付款方式;支付周期;付款条件',
            'regex': '支付.*\d+.*天',
            'checklist': '1. 确认付款方式\n2. 明确付款周期\n3. 设定逾期责任',
            'suggest_A': '建议明确指定付款方式和周期',
            'suggest_B': '建议要求提高付款保障机制'
        },
        {
            'contract_types': '采购;外包',
            'party_scope': 'Neutral',
            'risk': 'high',
            'keywords': '违约责任;赔偿;违约金',
            'regex': '违约.*赔偿.*\d+',
            'checklist': '1. 明确违约责任\n2. 设定赔偿标准\n3. 平衡双方责任',
            'suggest_A': '建议设定合理的违约金比例',
            'suggest_B': '建议要求明确违约情形和责任'
        },
        {
            'contract_types': '技术服务;外包',
            'party_scope': 'Neutral',
            'risk': 'high',
            'keywords': '知识产权;技术成果;著作权',
            'regex': '知识产权.*归属',
            'checklist': '1. 明确知识产权归属\n2. 区分项目代码和通用技术\n3. 设定使用许可条款',
            'suggest_A': '建议明确开发成果归属权',
            'suggest_B': '建议保护技术投入和知识产权'
        },
        {
            'contract_types': '通用合同',
            'party_scope': 'Neutral',
            'risk': 'medium',
            'keywords': '保密;商业秘密;机密信息',
            'regex': '保密.*\d+.*年',
            'checklist': '1. 定义保密信息范围\n2. 设定保密期限\n3. 明确违约责任',
            'suggest_A': '建议明确商业秘密定义',
            'suggest_B': '建议设定具体违约金额'
        },
        {
            'contract_types': '技术服务;采购',
            'party_scope': 'Neutral',
            'risk': 'medium',
            'keywords': '交付;验收;完成标准',
            'regex': '交付.*验收.*标准',
            'checklist': '1. 制定验收标准\n2. 设定验收时限\n3. 明确不合格处理',
            'suggest_A': '建议制定详细验收标准',
            'suggest_B': '建议设定合理验收时限'
        },
        {
            'contract_types': '通用合同',
            'party_scope': 'Neutral',
            'risk': 'medium',
            'keywords': '终止;解除;合同期限',
            'regex': '终止.*\d+.*天.*通知',
            'checklist': '1. 明确终止情形\n2. 设定通知期限\n3. 规定交接程序',
            'suggest_A': '建议明确终止条件',
            'suggest_B': '建议设定终止通知期限'
        },
        {
            'contract_types': '技术服务',
            'party_scope': 'Neutral',
            'risk': 'medium',
            'keywords': '数据安全;隐私保护;加密',
            'regex': '数据.*安全.*加密',
            'checklist': '1. 明确数据分类\n2. 规定加密要求\n3. 设定备份程序',
            'suggest_A': '建议明确数据保护等级',
            'suggest_B': '建议要求数据安全措施'
        },
        {
            'contract_types': '通用合同',
            'party_scope': 'Neutral',
            'risk': 'low',
            'keywords': '不可抗力;天灾;force majeure',
            'regex': '不可抗力.*\d+.*天',
            'checklist': '1. 定义不可抗力情形\n2. 设定通知义务\n3. 规定损失分担',
            'suggest_A': '建议明确不可抗力范围',
            'suggest_B': '建议设定通知程序'
        },
        {
            'contract_types': '通用合同',
            'party_scope': 'Neutral',
            'risk': 'low',
            'keywords': '争议;仲裁;诉讼;管辖',
            'regex': '争议.*仲裁.*管辖',
            'checklist': '1. 选择争议解决方式\n2. 明确管辖法院\n3. 设定前置程序',
            'suggest_A': '建议增加仲裁选项',
            'suggest_B': '建议明确管辖法院'
        },
        {
            'contract_types': '技术服务;外包',
            'party_scope': 'Neutral',
            'risk': 'low',
            'keywords': '沟通;联系;报告;通知',
            'regex': '沟通.*报告.*频率',
            'checklist': '1. 明确沟通方式\n2. 设定报告频率\n3. 规定紧急联系',
            'suggest_A': '建议明确沟通机制',
            'suggest_B': '建议设定项目报告制度'
        }
    ]
    
    return pd.DataFrame(sample_rules)

def convert_excel_to_rules(input_file, output_file):
    """转换Excel文件为rules格式"""
    
    print(f"=== 转换 {input_file} 为 {output_file} ===")
    
    try:
        # 尝试读取原文件
        if os.path.exists(input_file):
            print(f"正在读取原文件: {input_file}")
            df = pd.read_excel(input_file)
            print(f"✓ 成功读取，数据形状: {df.shape}")
            print(f"列名: {df.columns.tolist()}")
            
            # 显示前几行数据
            print("\n前3行数据:")
            for i in range(min(3, len(df))):
                print(f"\n第{i+1}行:")
                for col in df.columns:
                    value = df.iloc[i][col]
                    print(f"  {col}: {value}")
            
            # 智能转换（基于列名匹配）
            rules_df = smart_convert(df)
            
        else:
            print(f"原文件不存在: {input_file}")
            print("使用示例规则数据...")
            rules_df = create_sample_rules()
        
        # 确保输出目录存在
        os.makedirs(os.path.dirname(output_file), exist_ok=True)
        
        # 保存转换后的文件
        rules_df.to_excel(output_file, index=False, engine='openpyxl')
        print(f"✓ 成功保存到: {output_file}")
        
        # 显示转换结果
        print(f"\n转换结果:")
        print(f"数据形状: {rules_df.shape}")
        print(f"列名: {rules_df.columns.tolist()}")
        
        print("\n前3行转换后的数据:")
        print(rules_df.head(3))
        
        return True
        
    except Exception as e:
        print(f"✗ 转换失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def smart_convert(df):
    """智能转换原数据为rules格式"""
    
    target_columns = [
        'contract_types', 'party_scope', 'risk', 'keywords',
        'regex', 'checklist', 'suggest_A', 'suggest_B'
    ]
    
    rules_df = pd.DataFrame(columns=target_columns)
    
    # 分析原文件列名
    original_columns = df.columns.tolist()
    print(f"\n分析原文件列名: {original_columns}")
    
    # 智能映射
    column_mapping = {}
    for col in original_columns:
        col_lower = str(col).lower()
        
        # 合同类型映射
        if any(keyword in col_lower for keyword in ['合同', 'contract', '类型', 'type', '种类']):
            column_mapping['contract_types'] = col
        
        # 立场映射
        elif any(keyword in col_lower for keyword in ['立场', 'party', 'scope', '范围', '角度']):
            column_mapping['party_scope'] = col
        
        # 风险等级映射
        elif any(keyword in col_lower for keyword in ['风险', 'risk', '等级', 'level', '严重']):
            column_mapping['risk'] = col
        
        # 关键词映射
        elif any(keyword in col_lower for keyword in ['关键词', 'keyword', '关键字', '匹配']):
            column_mapping['keywords'] = col
        
        # 正则表达式映射
        elif any(keyword in col_lower for keyword in ['正则', 'regex', '表达式', '模式']):
            column_mapping['regex'] = col
        
        # 检查清单映射
        elif any(keyword in col_lower for keyword in ['检查', 'checklist', '清单', '要点', '项目']):
            column_mapping['checklist'] = col
        
        # 甲方建议映射
        elif any(keyword in col_lower for keyword in ['甲方', 'A方', 'suggest_a', '建议A']):
            column_mapping['suggest_A'] = col
        
        # 乙方建议映射
        elif any(keyword in col_lower for keyword in ['乙方', 'B方', 'suggest_b', '建议B']):
            column_mapping['suggest_B'] = col
    
    print(f"列名映射: {column_mapping}")
    
    # 转换每一行数据
    for index, row in df.iterrows():
        new_row = {}
        
        # 填充映射的列
        for target_col, source_col in column_mapping.items():
            if source_col in df.columns:
                value = row[source_col]
                if pd.notna(value):
                    new_row[target_col] = str(value).strip()
                else:
                    new_row[target_col] = ""
            else:
                new_row[target_col] = ""
        
        # 填充默认值
        if not new_row.get('contract_types'):
            new_row['contract_types'] = "通用合同"
        if not new_row.get('party_scope'):
            new_row['party_scope'] = "Neutral"
        if not new_row.get('risk'):
            new_row['risk'] = "medium"
        
        # 如果没有映射到关键列，尝试从其他列提取信息
        if not new_row.get('keywords') and not new_row.get('checklist'):
            # 尝试从评审意见中提取关键词
            for col in original_columns:
                if any(keyword in str(col).lower() for keyword in ['意见', '建议', '评审', 'review', 'comment']):
                    content = str(row[col]) if pd.notna(row[col]) else ""
                    if content:
                        # 提取关键词
                        keywords = extract_keywords(content)
                        if keywords:
                            new_row['keywords'] = keywords
                        
                        # 生成检查清单
                        checklist = generate_checklist(content)
                        if checklist:
                            new_row['checklist'] = checklist
                        break
        
        rules_df = pd.concat([rules_df, pd.DataFrame([new_row])], ignore_index=True)
    
    return rules_df

def extract_keywords(content):
    """从内容中提取关键词"""
    # 常见合同关键词
    common_keywords = [
        '付款', '支付', '费用', '价款', '结算',
        '违约', '责任', '赔偿', '违约金',
        '保密', '商业秘密', '机密信息',
        '知识产权', '著作权', '专利权',
        '交付', '验收', '完成', '标准',
        '终止', '解除', '期限',
        '数据', '安全', '隐私', '加密',
        '不可抗力', '天灾',
        '争议', '仲裁', '诉讼', '管辖',
        '沟通', '联系', '报告', '通知'
    ]
    
    found_keywords = []
    for keyword in common_keywords:
        if keyword in content:
            found_keywords.append(keyword)
    
    return ';'.join(found_keywords[:5]) if found_keywords else ""

def generate_checklist(content):
    """从内容中生成检查清单"""
    # 简单的检查清单生成
    checklist_items = []
    
    if '付款' in content or '支付' in content:
        checklist_items.append("1. 确认付款方式和周期")
        checklist_items.append("2. 明确逾期付款责任")
    
    if '违约' in content or '责任' in content:
        checklist_items.append("1. 明确违约责任")
        checklist_items.append("2. 设定赔偿标准")
    
    if '保密' in content:
        checklist_items.append("1. 定义保密信息范围")
        checklist_items.append("2. 设定保密期限")
    
    if '知识产权' in content:
        checklist_items.append("1. 明确知识产权归属")
        checklist_items.append("2. 设定使用许可条款")
    
    if '交付' in content or '验收' in content:
        checklist_items.append("1. 制定验收标准")
        checklist_items.append("2. 设定验收时限")
    
    return '\n'.join(checklist_items) if checklist_items else ""

def main():
    """主函数"""
    input_file = "合同评审意见列示.xlsx"
    output_file = "src/main/resources/review-rules/rules_converted.xlsx"
    
    success = convert_excel_to_rules(input_file, output_file)
    
    if success:
        print(f"\n✅ 转换完成！")
        print(f"原文件: {input_file}")
        print(f"转换后: {output_file}")
        print(f"\n使用说明:")
        print(f"1. 转换后的文件已保存到: {output_file}")
        print(f"2. 系统会自动加载新的规则文件")
        print(f"3. 可以在合同审查时使用这些规则")
        print(f"4. 支持关键字匹配和正则表达式匹配")
    else:
        print(f"\n❌ 转换失败！")

if __name__ == "__main__":
    main()
