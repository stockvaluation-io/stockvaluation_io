import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TableColumn {
  key: string;
  label: string;
  type?: 'text' | 'number' | 'currency' | 'percentage';
  format?: 'short' | 'long';
  precision?: number;
  sortable?: boolean;
  width?: string;
  align?: 'left' | 'center' | 'right';
}

export interface TableRow {
  [key: string]: any;
}

export interface TableConfig {
  showHeader?: boolean;
  showFooter?: boolean;
  striped?: boolean;
  hoverable?: boolean;
  borderless?: boolean;
  compact?: boolean;
  responsive?: boolean;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="data-table-container" [class]="getContainerClasses()">
      <div class="table-wrapper">
        <table class="data-table" [class]="getTableClasses()">
          <thead *ngIf="config.showHeader !== false">
            <tr>
              <th *ngFor="let column of columns; trackBy: trackByColumn"
                  [style.width]="column.width"
                  [class]="getColumnClasses(column)"
                  (click)="onSort(column)">
                <div class="th-content">
                  <span>{{ column.label }}</span>
                  <i *ngIf="column.sortable && sortColumn === column.key"
                     [class]="getSortIcon()"
                     class="sort-icon"></i>
                </div>
              </th>
            </tr>
          </thead>
          
          <tbody>
            <tr *ngFor="let row of sortedData; trackBy: trackByRow; let i = index"
                [class]="getRowClasses(row, i)"
                (click)="onRowClick(row, i)">
              <td *ngFor="let column of columns; trackBy: trackByColumn"
                  [class]="getColumnClasses(column)">
                <div class="td-content">
                  {{ getFormattedValue(row[column.key], column) }}
                </div>
              </td>
            </tr>
          </tbody>
          
          <tfoot *ngIf="config.showFooter && footerData">
            <tr class="footer-row">
              <td *ngFor="let column of columns; trackBy: trackByColumn"
                  [class]="getColumnClasses(column)">
                <div class="td-content footer-content">
                  {{ getFormattedValue(footerData[column.key], column) }}
                </div>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
      
      <div *ngIf="isLoading" class="table-loading">
        <div class="loading-overlay">
          <i class="pi pi-spin pi-spinner"></i>
          <span>Loading data...</span>
        </div>
      </div>
      
      <div *ngIf="!isLoading && (!data || data.length === 0)" class="table-empty">
        <i class="pi pi-database"></i>
        <p>No data available</p>
      </div>
    </div>
  `,
  styleUrls: ['./data-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataTableComponent {
  @Input() columns: TableColumn[] = [];
  @Input() data: TableRow[] = [];
  @Input() footerData?: TableRow;
  @Input() config: TableConfig = {};
  @Input() isLoading = false;
  
  @Output() rowClick = new EventEmitter<{row: TableRow, index: number}>();
  @Output() sort = new EventEmitter<{column: string, direction: 'asc' | 'desc'}>();

  sortColumn: string | null = null;
  sortDirection: 'asc' | 'desc' = 'asc';

  get sortedData(): TableRow[] {
    if (!this.sortColumn || !this.data) {
      return this.data || [];
    }

    return [...this.data].sort((a, b) => {
      const aVal = a[this.sortColumn!];
      const bVal = b[this.sortColumn!];
      
      if (aVal === bVal) return 0;
      
      const comparison = aVal < bVal ? -1 : 1;
      return this.sortDirection === 'asc' ? comparison : -comparison;
    });
  }

  getContainerClasses(): string {
    const classes = [];
    if (this.config.responsive !== false) classes.push('responsive');
    return classes.join(' ');
  }

  getTableClasses(): string {
    const classes = [];
    if (this.config.striped) classes.push('striped');
    if (this.config.hoverable !== false) classes.push('hoverable');
    if (this.config.borderless) classes.push('borderless');
    if (this.config.compact) classes.push('compact');
    return classes.join(' ');
  }

  getColumnClasses(column: TableColumn): string {
    const classes = [];
    if (column.align) classes.push(`align-${column.align}`);
    if (column.sortable) classes.push('sortable');
    return classes.join(' ');
  }

  getRowClasses(row: TableRow, index: number): string {
    const classes = [];
    if (row['_highlighted']) classes.push('highlighted');
    if (row['_status']) classes.push(`status-${row['_status']}`);
    return classes.join(' ');
  }

  onSort(column: TableColumn): void {
    if (!column.sortable) return;

    if (this.sortColumn === column.key) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column.key;
      this.sortDirection = 'asc';
    }

    this.sort.emit({
      column: column.key,
      direction: this.sortDirection
    });
  }

  onRowClick(row: TableRow, index: number): void {
    this.rowClick.emit({ row, index });
  }

  getSortIcon(): string {
    return this.sortDirection === 'asc' ? 'pi pi-sort-up' : 'pi pi-sort-down';
  }

  getFormattedValue(value: any, column: TableColumn): string {
    if (value === null || value === undefined) return '-';

    switch (column.type) {
      case 'currency':
        return this.formatCurrency(value, column.precision ?? 2, column.format);
      case 'percentage':
        const percentValue = Number(value);
        return isNaN(percentValue) ? '-' : `${percentValue.toFixed(column.precision ?? 1)}%`;
      case 'number':
        return this.formatNumber(value, column.precision ?? 2, column.format);
      default:
        return String(value);
    }
  }

  private formatCurrency(value: any, precision: number, format?: string): string {
    const numValue = Number(value);
    if (isNaN(numValue)) return '-';
    
    if (format === 'short') {
      if (numValue >= 1e9) return `$${(numValue / 1e9).toFixed(precision)}B`;
      if (numValue >= 1e6) return `$${(numValue / 1e6).toFixed(precision)}M`;
      if (numValue >= 1e3) return `$${(numValue / 1e3).toFixed(precision)}K`;
    }
    return `$${numValue.toFixed(precision)}`;
  }

  private formatNumber(value: any, precision: number, format?: string): string {
    const numValue = Number(value);
    if (isNaN(numValue)) return '-';
    
    if (format === 'short') {
      if (numValue >= 1e9) return `${(numValue / 1e9).toFixed(precision)}B`;
      if (numValue >= 1e6) return `${(numValue / 1e6).toFixed(precision)}M`;
      if (numValue >= 1e3) return `${(numValue / 1e3).toFixed(precision)}K`;
    }
    return numValue.toFixed(precision);
  }

  trackByColumn(index: number, column: TableColumn): string {
    return column.key;
  }

  trackByRow(index: number, row: TableRow): any {
    return row['id'] || index;
  }
}