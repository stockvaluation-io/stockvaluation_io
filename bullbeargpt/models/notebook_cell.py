"""
NotebookCell model - Jupyter-style cells for analysis notebook.
Supports cell types: reasoning, calibration, visualization, system, computation.
"""
import uuid
from datetime import datetime
from typing import Optional, Dict, Any, Literal
from dataclasses import dataclass, field
import json


CellType = Literal['reasoning', 'calibration', 'computation', 'visualization', 'system']
AuthorType = Literal['ai', 'user', 'system']


@dataclass
class NotebookCell:
    """
    Individual cell in the analysis notebook.
    Unified cell containing both user input and AI output.
    """
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    session_id: str = ""
    sequence_number: int = 0
    cell_type: CellType = 'reasoning'
    
    # Unified cell structure
    user_input: Optional[str] = None  # User's question/prompt (editable)
    ai_output: Optional[Dict[str, Any]] = None  # AI's response (regeneratable)
    user_notes: Optional[str] = None  # User annotations/notes
    
    # Content is flexible JSONB (for backward compatibility)
    content: Dict[str, Any] = field(default_factory=dict)
    
    # DCF State Management
    dcf_snapshot_id: Optional[str] = None
    changed_parameters: Dict[str, Any] = field(default_factory=dict)
    
    # Provenance
    author_type: AuthorType = 'ai'
    parent_cell_id: Optional[str] = None  # For branching within session
    
    # Metadata
    created_at: datetime = field(default_factory=datetime.utcnow)
    execution_time_ms: Optional[int] = None
    
    # Runtime fields (not persisted)
    is_streaming: bool = False
    
    def is_editable(self) -> bool:
        """Check if cell can be edited (system cells cannot be edited)."""
        return self.cell_type != 'system' and self.author_type != 'system'
    
    def is_deletable(self) -> bool:
        """Check if cell can be deleted (system cells cannot be deleted)."""
        return self.cell_type != 'system' and self.author_type != 'system'
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization."""
        return {
            'id': self.id,
            'session_id': self.session_id,
            'sequence_number': self.sequence_number,
            'cell_type': self.cell_type,
            'user_input': self.user_input,
            'ai_output': self.ai_output,
            'user_notes': self.user_notes,
            'content': self.content,
            'dcf_snapshot_id': self.dcf_snapshot_id,
            'changed_parameters': self.changed_parameters,
            'author_type': self.author_type,
            'parent_cell_id': self.parent_cell_id,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'execution_time_ms': self.execution_time_ms,
            'is_streaming': self.is_streaming,
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'NotebookCell':
        """Create from dictionary."""
        cell = cls(
            id=data.get('id', str(uuid.uuid4())),
            session_id=data.get('session_id', ''),
            sequence_number=data.get('sequence_number', 0),
            cell_type=data.get('cell_type', 'reasoning'),
            user_input=data.get('user_input'),
            ai_output=data.get('ai_output'),
            user_notes=data.get('user_notes'),
            content=data.get('content', {}),
            dcf_snapshot_id=data.get('dcf_snapshot_id'),
            changed_parameters=data.get('changed_parameters', {}),
            author_type=data.get('author_type', 'ai'),
            parent_cell_id=data.get('parent_cell_id'),
            execution_time_ms=data.get('execution_time_ms'),
        )
        
        if data.get('created_at'):
            if isinstance(data['created_at'], str):
                cell.created_at = datetime.fromisoformat(data['created_at'].replace('Z', '+00:00'))
            else:
                cell.created_at = data['created_at']
        
        return cell
    
    def to_supabase_dict(self) -> Dict[str, Any]:
        """Convert to Supabase-compatible dictionary."""
        return {
            'id': self.id,
            'session_id': self.session_id,
            'sequence_number': self.sequence_number,
            'cell_type': self.cell_type,
            'user_input': self.user_input,
            'ai_output': self.ai_output,
            'user_notes': self.user_notes,
            'content': self.content,
            'dcf_snapshot_id': self.dcf_snapshot_id,
            'changed_parameters': self.changed_parameters,
            'author_type': self.author_type,
            'parent_cell_id': self.parent_cell_id,
            'execution_time_ms': self.execution_time_ms,
        }
    
    @classmethod
    def create_reasoning_cell(
        cls,
        session_id: str,
        user_input: str,
        sequence: int,
        ai_output: Optional[Dict[str, Any]] = None
    ) -> 'NotebookCell':
        """Factory method to create a reasoning cell with user input and AI output."""
        return cls(
            session_id=session_id,
            sequence_number=sequence,
            cell_type='reasoning',
            user_input=user_input,
            ai_output=ai_output or {},
            author_type='user',
            content={
                'user_input': user_input,
                'ai_output': ai_output or {},
                'type': 'reasoning'
            }
        )
    
    @classmethod
    def create_calibration_cell(
        cls, 
        session_id: str, 
        changes: Dict[str, Any], 
        dcf_snapshot_id: str,
        sequence: int,
        rationale: str = ""
    ) -> 'NotebookCell':
        """Factory method to create a DCF calibration cell."""
        return cls(
            session_id=session_id,
            sequence_number=sequence,
            cell_type='calibration',
            content={
                'changes': changes,
                'rationale': rationale,
            },
            dcf_snapshot_id=dcf_snapshot_id,
            changed_parameters=changes,
            author_type='ai',
        )
    
    @classmethod
    def create_system_cell(
        cls,
        session_id: str,
        sequence: int,
        message: str,
        system_type: str = 'info',  # 'welcome', 'news', 'dcf_summary', 'info'
        dcf_summary: Optional[Dict[str, Any]] = None
    ) -> 'NotebookCell':
        """Factory method to create a system cell."""
        return cls(
            session_id=session_id,
            sequence_number=sequence,
            cell_type='system',
            content={
                'message': message,
                'type': system_type,
                'dcf_summary': dcf_summary or {}
            },
            author_type='system',
        )
    
    @classmethod
    def create_visualization_cell(
        cls,
        session_id: str,
        sequence: int,
        chart_type: str,
        chart_data: Dict[str, Any]
    ) -> 'NotebookCell':
        """Factory method to create a visualization cell."""
        return cls(
            session_id=session_id,
            sequence_number=sequence,
            cell_type='visualization',
            content={
                'chart_type': chart_type,
                'chart_data': chart_data,
            },
            author_type='ai',
        )
