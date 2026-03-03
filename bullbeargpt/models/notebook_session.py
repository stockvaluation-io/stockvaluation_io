"""
NotebookSession model - Represents a notebook/analysis session.
"""
import uuid
from datetime import datetime
from typing import Optional, Dict, Any
from dataclasses import dataclass, field


@dataclass
class NotebookSession:
    """
    Represents a notebook analysis session.
    Contains metadata and references to cells.
    """
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    ticker: str = ""
    company_name: Optional[str] = None
    user_id: Optional[str] = None
    title: Optional[str] = None
    
    created_at: datetime = field(default_factory=datetime.utcnow)
    updated_at: datetime = field(default_factory=datetime.utcnow)
    
    is_public: bool = False
    parent_session_id: Optional[str] = None  # For forking
    base_analysis_json: Optional[Dict[str, Any]] = None  # Initial DCF data
    currency: Optional[str] = None
    valuation_id: Optional[str] = None  # Links to yfinance-generated base valuation
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization."""
        return {
            'id': self.id,
            'ticker': self.ticker,
            'company_name': self.company_name,
            'user_id': self.user_id,
            'title': self.title,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
            'is_public': self.is_public,
            'parent_session_id': self.parent_session_id,
            'base_analysis_json': self.base_analysis_json,
            'currency': self.currency,
            'valuation_id': self.valuation_id,
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'NotebookSession':
        """Create from dictionary."""
        session = cls(
            id=data.get('id', str(uuid.uuid4())),
            ticker=data.get('ticker', ''),
            company_name=data.get('company_name'),
            user_id=data.get('user_id'),
            title=data.get('title'),
            is_public=data.get('is_public', False),
            parent_session_id=data.get('parent_session_id'),
            base_analysis_json=data.get('base_analysis_json'),
            currency=data.get('currency'),
            valuation_id=data.get('valuation_id'),
        )
        
        if data.get('created_at'):
            if isinstance(data['created_at'], str):
                session.created_at = datetime.fromisoformat(data['created_at'].replace('Z', '+00:00'))
            else:
                session.created_at = data['created_at']
        
        if data.get('updated_at'):
            if isinstance(data['updated_at'], str):
                session.updated_at = datetime.fromisoformat(data['updated_at'].replace('Z', '+00:00'))
            else:
                session.updated_at = data['updated_at']
        
        return session
    
    @classmethod
    def create(
        cls,
        ticker: str,
        company_name: Optional[str] = None,
        user_id: Optional[str] = None,
        valuation_data: Optional[Dict[str, Any]] = None,
        currency: Optional[str] = None,
        valuation_id: Optional[str] = None,
        session_id: Optional[str] = None
    ) -> 'NotebookSession':
        """Factory method to create a new session."""
        title = f"{company_name or ticker} Analysis" if company_name else f"{ticker} Analysis"
        return cls(
            id=session_id or str(uuid.uuid4()),  # Use provided session_id or generate random UUID
            ticker=ticker,
            company_name=company_name,
            user_id=user_id,
            title=title,
            base_analysis_json=valuation_data,
            currency=currency,
            valuation_id=valuation_id,
        )
