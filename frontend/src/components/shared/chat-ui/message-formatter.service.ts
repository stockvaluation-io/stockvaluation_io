/**
 * Message Formatter Service
 * Parses AI responses and extracts structured data for enhanced display
 */

import { Injectable } from '@angular/core';

export interface FormattedMessage {
  raw: string;
  sections: MessageSection[];
  keyMetrics: FinancialMetric[];
  suggestedActions: QuickAction[];
  hasStructuredContent: boolean;
}

export interface MessageSection {
  type: 'math' | 'analysis' | 'question' | 'concern' | 'hypothesis' | 'general';
  icon: string;
  title: string;
  content: string;
  collapsible: boolean;
  defaultExpanded?: boolean;
}

export interface FinancialMetric {
  label: string;
  value: string;
  comparison?: string;
  change?: number;
  visualType?: 'growth-arrow' | 'comparison-bar' | 'sparkline' | 'badge';
  color?: 'success' | 'warning' | 'danger' | 'info';
}

export interface QuickAction {
  icon: string;
  label: string;
  action: string;
  params?: any;
  primary?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class MessageFormatterService {

  /**
   * Parse a message and extract structured data
   */
  parseMessage(content: string): FormattedMessage {
    const sections = this.detectSections(content);
    const keyMetrics = this.extractMetrics(content);
    const suggestedActions = this.suggestActions(content, sections, keyMetrics);
    
    return {
      raw: content,
      sections,
      keyMetrics,
      suggestedActions,
      hasStructuredContent: sections.length > 1 || keyMetrics.length > 0
    };
  }

  /**
   * Enhance markdown content with bold metrics and better structure
   */
  enhanceMarkdown(content: string): string {
    let enhanced = content;
    
    // Bold financial metrics
    // Matches: $165B, $1.1T, 22%, 15.5%, etc.
    enhanced = enhanced.replace(/(\$[\d,]+(?:\.\d+)?[BMTbmt]?)/g, '**$1**');
    enhanced = enhanced.replace(/(\d+(?:\.\d+)?%)/g, '**$1**');
    
    // Convert section headers to markdown headers
    // Matches: THE MATH:, THE CHALLENGE:, CRITICAL QUESTION:, etc.
    enhanced = enhanced.replace(/^([A-Z][A-Z\s]+):$/gm, '### $1');
    
    // Highlight growth indicators with arrows
    enhanced = enhanced.replace(/(\$[\d,]+[BMT]?)\s*→\s*(\$[\d,]+[BMT]?)/g, '**$1** → **$2**');
    
    // Highlight questions at the end
    const lines = enhanced.split('\n');
    if (lines.length > 0 && lines[lines.length - 1].includes('?')) {
      const lastLine = lines[lines.length - 1];
      lines[lines.length - 1] = `\n**${lastLine}**`;
      enhanced = lines.join('\n');
    }
    
    return enhanced;
  }

  /**
   * Detect and parse sections in the message
   */
  private detectSections(content: string): MessageSection[] {
    const sections: MessageSection[] = [];
    
    // Check for explicit section headers (all caps followed by colon)
    const sectionPattern = /^([A-Z][A-Z\s]+):\s*$/gm;
    const matches = Array.from(content.matchAll(sectionPattern));
    
    if (matches.length === 0) {
      // No explicit sections, return content as single section
      return [];
    }
    
    // Parse sections
    for (let i = 0; i < matches.length; i++) {
      const match = matches[i];
      const sectionTitle = match[1].trim();
      const sectionStart = match.index! + match[0].length;
      const sectionEnd = i < matches.length - 1 ? matches[i + 1].index! : content.length;
      const sectionContent = content.substring(sectionStart, sectionEnd).trim();
      
      const sectionType = this.determineSectionType(sectionTitle);
      
      sections.push({
        type: sectionType,
        icon: this.getSectionIcon(sectionType),
        title: this.formatSectionTitle(sectionTitle),
        content: sectionContent,
        collapsible: sectionContent.length > 300,
        defaultExpanded: sectionType === 'question' || sectionContent.length < 300
      });
    }
    
    return sections;
  }

  /**
   * Extract financial metrics from content
   */
  private extractMetrics(content: string): FinancialMetric[] {
    const metrics: FinancialMetric[] = [];
    
    // Extract growth patterns: $X → $Y
    const growthPattern = /(\$[\d,]+(?:\.\d+)?[BMTbmt]?)\s*→\s*(\$[\d,]+(?:\.\d+)?[BMTbmt]?)/g;
    let match;
    
    while ((match = growthPattern.exec(content)) !== null) {
      const start = match[1];
      const end = match[2];
      const startNum = this.parseFinancialValue(start);
      const endNum = this.parseFinancialValue(end);
      const change = startNum > 0 ? ((endNum - startNum) / startNum) * 100 : 0;
      
      metrics.push({
        label: 'Growth',
        value: `${start} → ${end}`,
        change: change,
        visualType: 'growth-arrow',
        color: change > 0 ? 'success' : change < 0 ? 'danger' : 'info'
      });
    }
    
    // Extract CAGR patterns: 22% CAGR
    const cagrPattern = /(\d+(?:\.\d+)?%)\s*CAGR/gi;
    while ((match = cagrPattern.exec(content)) !== null) {
      const value = match[1];
      const numValue = parseFloat(value);
      
      metrics.push({
        label: 'CAGR',
        value: value,
        change: numValue,
        visualType: 'badge',
        color: numValue > 20 ? 'success' : numValue > 10 ? 'info' : 'warning'
      });
    }
    
    // Extract valuation gaps: Price vs value comparisons
    const priceValuePattern = /\$(\d+(?:\.\d+)?)\s*vs\.?\s*\$(\d+(?:\.\d+)?)/gi;
    while ((match = priceValuePattern.exec(content)) !== null) {
      const price = parseFloat(match[1]);
      const value = parseFloat(match[2]);
      const gap = ((price - value) / value) * 100;
      
      metrics.push({
        label: 'Valuation Gap',
        value: `${gap > 0 ? '+' : ''}${gap.toFixed(1)}%`,
        comparison: `$${price} vs $${value}`,
        change: gap,
        visualType: 'comparison-bar',
        color: Math.abs(gap) < 10 ? 'info' : gap < 0 ? 'success' : 'danger'
      });
    }
    
    return metrics;
  }

  /**
   * Suggest contextual actions based on message content
   */
  private suggestActions(content: string, sections: MessageSection[], metrics: FinancialMetric[]): QuickAction[] {
    const actions: QuickAction[] = [];
    const lowerContent = content.toLowerCase();
    
    // Model-related actions - COMMENTED OUT per user request
    // if (lowerContent.match(/model|projection|forecast|dcf|valuation/i)) {
    //   actions.push({
    //     icon: 'pi pi-chart-line',
    //     label: 'View DCF Model',
    //     action: 'show_dcf_model',
    //     primary: true
    //   });
    // }
    
    // Scenario actions
    if (lowerContent.match(/scenario|what if|alternative|upside|downside|bull|bear/i)) {
      actions.push({
        icon: 'pi pi-sliders-h',
        label: 'Run Scenarios',
        action: 'open_scenarios'
      });
    }
    
    // Growth analysis
    if (lowerContent.match(/growth|revenue|cagr|expand/i)) {
      actions.push({
        icon: 'pi pi-chart-bar',
        label: 'Growth Analysis',
        action: 'analyze_growth'
      });
    }
    
    // Risk analysis
    if (lowerContent.match(/risk|concern|challenge|problem|weakness/i)) {
      actions.push({
        icon: 'pi pi-exclamation-triangle',
        label: 'Risk Assessment',
        action: 'assess_risks'
      });
    }
    
    // Thesis saving (always available for substantial responses)
    if (content.length > 200 || sections.some(s => s.type === 'hypothesis')) {
      actions.push({
        icon: 'pi pi-save',
        label: 'Save Analysis',
        action: 'save_thesis'
      });
    }
    
    return actions;
  }

  /**
   * Determine section type from title
   */
  private determineSectionType(title: string): MessageSection['type'] {
    const lower = title.toLowerCase();
    
    if (lower.includes('math') || lower.includes('numbers') || lower.includes('calculation')) {
      return 'math';
    }
    if (lower.includes('question') || lower.includes('ask') || lower.includes('consider')) {
      return 'question';
    }
    if (lower.includes('concern') || lower.includes('challenge') || lower.includes('risk') || lower.includes('problem')) {
      return 'concern';
    }
    if (lower.includes('hypothesis') || lower.includes('thesis') || lower.includes('view')) {
      return 'hypothesis';
    }
    if (lower.includes('analysis') || lower.includes('assessment') || lower.includes('evaluation')) {
      return 'analysis';
    }
    
    return 'general';
  }

  /**
   * Get icon for section type
   */
  private getSectionIcon(type: MessageSection['type']): string {
    const icons: Record<MessageSection['type'], string> = {
      math: 'pi pi-calculator',
      analysis: 'pi pi-chart-line',
      question: 'pi pi-question-circle',
      concern: 'pi pi-exclamation-triangle',
      hypothesis: 'pi pi-lightbulb',
      general: 'pi pi-info-circle'
    };
    
    return icons[type];
  }

  /**
   * Format section title for display
   */
  private formatSectionTitle(title: string): string {
    return title
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  /**
   * Parse financial value string to number
   */
  private parseFinancialValue(value: string): number {
    // Remove $, commas
    let cleaned = value.replace(/[$,]/g, '');
    
    // Handle B (billions), M (millions), T (trillions)
    const multipliers: Record<string, number> = {
      'b': 1e9,
      'm': 1e6,
      't': 1e12
    };
    
    const suffix = cleaned.slice(-1).toLowerCase();
    if (multipliers[suffix]) {
      return parseFloat(cleaned.slice(0, -1)) * multipliers[suffix];
    }
    
    return parseFloat(cleaned) || 0;
  }
}

