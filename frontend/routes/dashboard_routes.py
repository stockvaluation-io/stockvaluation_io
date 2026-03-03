"""
Dashboard API Routes
Handles user's investment theses dashboard endpoints
"""

from flask import Blueprint, request, jsonify
from functools import wraps
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Any
import jwt
import os

# Initialize blueprint
dashboard_bp = Blueprint('dashboard', __name__, url_prefix='/api/dashboard')

# Supabase client (assumes you have this configured)
try:
    from supabase import create_client, Client
    SUPABASE_URL = os.getenv('SUPABASE_URL')
    SUPABASE_KEY = os.getenv('SUPABASE_SERVICE_KEY')
    supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
except Exception as e:
    print(f"Warning: Supabase client not initialized: {e}")
    supabase = None


# Authentication decorator
def require_auth(f):
    """Verify Supabase JWT token and extract user_id"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        
        if not auth_header or not auth_header.startswith('Bearer '):
            return jsonify({'error': 'Missing or invalid authorization header'}), 401
        
        token = auth_header.split('Bearer ')[1]
        
        try:
            # Verify JWT with Supabase
            SUPABASE_JWT_SECRET = os.getenv('SUPABASE_JWT_SECRET')
            payload = jwt.decode(
                token,
                SUPABASE_JWT_SECRET,
                algorithms=['HS256'],
                audience='authenticated'
            )
            
            # Extract user_id from token
            user_id = payload.get('sub')
            if not user_id:
                return jsonify({'error': 'Invalid token payload'}), 401
            
            # Add user_id to request context
            request.user_id = user_id
            return f(*args, **kwargs)
            
        except jwt.ExpiredSignatureError:
            return jsonify({'error': 'Token has expired'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'error': 'Invalid token'}), 401
        except Exception as e:
            return jsonify({'error': f'Authentication failed: {str(e)}'}), 401
    
    return decorated_function


# Utility functions
def calculate_return_percentage(initial_price: float, final_price: float) -> float:
    """Calculate percentage return"""
    if initial_price == 0:
        return 0.0
    return ((final_price - initial_price) / initial_price) * 100


def format_thesis_response(thesis: Dict[str, Any]) -> Dict[str, Any]:
    """Format thesis data for API response"""
    return {
        'id': thesis.get('id'),
        'ticker': thesis.get('ticker'),
        'thesis': thesis.get('thesis'),
        'conviction': thesis.get('conviction'),
        'timeframe': thesis.get('timeframe'),
        'created_at': thesis.get('created_at'),
        'updated_at': thesis.get('updated_at'),
        'outcome': thesis.get('outcome', 'pending'),
        'actual_return': thesis.get('actual_return'),
        'target_price': thesis.get('target_price'),
        'entry_price': thesis.get('entry_price'),
        'expected_return': thesis.get('expected_return'),
        'status': thesis.get('status', 'active'),
        'notes': thesis.get('notes'),
    }


# =============================================================================
# Dashboard Routes
# =============================================================================

@dashboard_bp.route('/theses', methods=['GET'])
@require_auth
def get_user_theses():
    """
    GET /api/dashboard/theses
    
    Returns user's investment theses
    
    Query Parameters:
        - limit (int): Number of results to return (default: 50)
        - offset (int): Pagination offset (default: 0)
        - ticker (str): Filter by specific ticker (optional)
        - status (str): Filter by status: all|pending|closed (default: all)
        - sort_by (str): Sort field: date|ticker|conviction (default: date)
        - order (str): Sort order: asc|desc (default: desc)
    """
    try:
        user_id = request.user_id
        
        # Parse query parameters
        limit = min(int(request.args.get('limit', 50)), 100)  # Max 100
        offset = int(request.args.get('offset', 0))
        ticker = request.args.get('ticker', '').upper().strip()
        status = request.args.get('status', 'all').lower()
        sort_by = request.args.get('sort_by', 'date').lower()
        order = request.args.get('order', 'desc').lower()
        
        # Build query
        query = supabase.table('investment_theses').select('*')
        query = query.eq('user_id', user_id)
        
        # Apply filters
        if ticker:
            query = query.eq('ticker', ticker)
        
        if status != 'all':
            if status == 'pending':
                query = query.eq('outcome', 'pending')
            elif status == 'closed':
                query = query.in_('outcome', ['success', 'failure'])
        
        # Apply sorting
        sort_column_map = {
            'date': 'created_at',
            'ticker': 'ticker',
            'conviction': 'conviction',
        }
        sort_column = sort_column_map.get(sort_by, 'created_at')
        
        query = query.order(sort_column, desc=(order == 'desc'))
        
        # Apply pagination
        query = query.range(offset, offset + limit - 1)
        
        # Execute query
        response = query.execute()
        theses = response.data
        
        # Format response
        formatted_theses = [format_thesis_response(thesis) for thesis in theses]
        
        return jsonify({
            'success': True,
            'data': formatted_theses,
            'pagination': {
                'limit': limit,
                'offset': offset,
                'count': len(formatted_theses),
            }
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@dashboard_bp.route('/stats', methods=['GET'])
@require_auth
def get_user_stats():
    """
    GET /api/dashboard/stats
    
    Returns user statistics
    
    Response:
        - total_theses: Total number of theses
        - pending_theses: Number of pending theses
        - closed_theses: Number of closed theses
        - win_rate: Percentage of successful theses
        - average_conviction: Average conviction score
        - average_return: Average actual return (closed theses only)
        - best_thesis: Best performing thesis
        - worst_thesis: Worst performing thesis
        - recent_activity: Recent thesis activity
    """
    try:
        user_id = request.user_id
        
        # Get all theses for user
        response = supabase.table('investment_theses')\
            .select('*')\
            .eq('user_id', user_id)\
            .execute()
        
        theses = response.data
        
        if not theses:
            return jsonify({
                'success': True,
                'data': {
                    'total_theses': 0,
                    'pending_theses': 0,
                    'closed_theses': 0,
                    'win_rate': 0.0,
                    'average_conviction': 0.0,
                    'average_return': 0.0,
                    'best_thesis': None,
                    'worst_thesis': None,
                    'recent_activity': []
                }
            }), 200
        
        # Calculate statistics
        total_theses = len(theses)
        pending_theses = sum(1 for t in theses if t.get('outcome') == 'pending')
        closed_theses = sum(1 for t in theses if t.get('outcome') in ['success', 'failure'])
        
        # Win rate calculation
        successful_theses = sum(1 for t in theses if t.get('outcome') == 'success')
        win_rate = (successful_theses / closed_theses * 100) if closed_theses > 0 else 0.0
        
        # Average conviction
        convictions = [t.get('conviction', 0) for t in theses if t.get('conviction')]
        average_conviction = sum(convictions) / len(convictions) if convictions else 0.0
        
        # Average return (for closed theses with actual_return)
        closed_with_returns = [
            t.get('actual_return', 0) 
            for t in theses 
            if t.get('outcome') in ['success', 'failure'] and t.get('actual_return') is not None
        ]
        average_return = sum(closed_with_returns) / len(closed_with_returns) if closed_with_returns else 0.0
        
        # Best and worst thesis
        theses_with_returns = [
            t for t in theses 
            if t.get('actual_return') is not None
        ]
        
        best_thesis = None
        worst_thesis = None
        
        if theses_with_returns:
            best = max(theses_with_returns, key=lambda t: t.get('actual_return', 0))
            worst = min(theses_with_returns, key=lambda t: t.get('actual_return', 0))
            
            best_thesis = {
                'ticker': best.get('ticker'),
                'return': best.get('actual_return'),
                'conviction': best.get('conviction'),
                'created_at': best.get('created_at'),
            }
            
            worst_thesis = {
                'ticker': worst.get('ticker'),
                'return': worst.get('actual_return'),
                'conviction': worst.get('conviction'),
                'created_at': worst.get('created_at'),
            }
        
        # Recent activity (last 5 theses)
        recent_theses = sorted(
            theses,
            key=lambda t: t.get('created_at', ''),
            reverse=True
        )[:5]
        
        recent_activity = [
            {
                'ticker': t.get('ticker'),
                'outcome': t.get('outcome'),
                'created_at': t.get('created_at'),
            }
            for t in recent_theses
        ]
        
        return jsonify({
            'success': True,
            'data': {
                'total_theses': total_theses,
                'pending_theses': pending_theses,
                'closed_theses': closed_theses,
                'win_rate': round(win_rate, 2),
                'average_conviction': round(average_conviction, 2),
                'average_return': round(average_return, 2),
                'best_thesis': best_thesis,
                'worst_thesis': worst_thesis,
                'recent_activity': recent_activity,
            }
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@dashboard_bp.route('/thesis/<thesis_id>', methods=['PATCH'])
@require_auth
def update_thesis_outcome(thesis_id: str):
    """
    PATCH /api/dashboard/thesis/<thesis_id>
    
    Update thesis outcome after checking back
    
    Body:
        - outcome (str): success|failure|pending
        - actual_return (float): Actual return percentage (optional)
        - notes (str): Additional notes (optional)
    """
    try:
        user_id = request.user_id
        data = request.get_json()
        
        if not data:
            return jsonify({
                'success': False,
                'error': 'Request body is required'
            }), 400
        
        # Validate outcome
        outcome = data.get('outcome', '').lower()
        valid_outcomes = ['success', 'failure', 'pending']
        
        if outcome not in valid_outcomes:
            return jsonify({
                'success': False,
                'error': f'Invalid outcome. Must be one of: {", ".join(valid_outcomes)}'
            }), 400
        
        # Build update data
        update_data = {
            'outcome': outcome,
            'updated_at': datetime.utcnow().isoformat(),
        }
        
        # Add optional fields
        if 'actual_return' in data:
            try:
                update_data['actual_return'] = float(data['actual_return'])
            except (ValueError, TypeError):
                return jsonify({
                    'success': False,
                    'error': 'actual_return must be a number'
                }), 400
        
        if 'notes' in data:
            update_data['notes'] = str(data['notes'])
        
        # Update thesis (with user_id check for security)
        response = supabase.table('investment_theses')\
            .update(update_data)\
            .eq('id', thesis_id)\
            .eq('user_id', user_id)\
            .execute()
        
        if not response.data:
            return jsonify({
                'success': False,
                'error': 'Thesis not found or unauthorized'
            }), 404
        
        updated_thesis = response.data[0]
        
        return jsonify({
            'success': True,
            'data': format_thesis_response(updated_thesis),
            'message': 'Thesis updated successfully'
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@dashboard_bp.route('/thesis/<thesis_id>', methods=['DELETE'])
@require_auth
def delete_thesis(thesis_id: str):
    """
    DELETE /api/dashboard/thesis/<thesis_id>
    
    Delete a thesis
    """
    try:
        user_id = request.user_id
        
        # Delete thesis (with user_id check for security)
        response = supabase.table('investment_theses')\
            .delete()\
            .eq('id', thesis_id)\
            .eq('user_id', user_id)\
            .execute()
        
        if not response.data:
            return jsonify({
                'success': False,
                'error': 'Thesis not found or unauthorized'
            }), 404
        
        return jsonify({
            'success': True,
            'message': 'Thesis deleted successfully'
        }), 200
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


@dashboard_bp.route('/thesis', methods=['POST'])
@require_auth
def create_thesis():
    """
    POST /api/dashboard/thesis
    
    Create a new investment thesis
    
    Body:
        - ticker (str): Stock ticker symbol (required)
        - thesis (str): Investment thesis text (required)
        - conviction (int): Conviction score 1-10 (required)
        - timeframe (str): Investment timeframe (optional)
        - target_price (float): Target price (optional)
        - entry_price (float): Entry price (optional)
        - expected_return (float): Expected return percentage (optional)
    """
    try:
        user_id = request.user_id
        data = request.get_json()
        
        if not data:
            return jsonify({
                'success': False,
                'error': 'Request body is required'
            }), 400
        
        # Validate required fields
        required_fields = ['ticker', 'thesis', 'conviction']
        for field in required_fields:
            if field not in data:
                return jsonify({
                    'success': False,
                    'error': f'Missing required field: {field}'
                }), 400
        
        # Validate conviction score
        try:
            conviction = int(data['conviction'])
            if not 1 <= conviction <= 10:
                raise ValueError()
        except (ValueError, TypeError):
            return jsonify({
                'success': False,
                'error': 'Conviction must be an integer between 1 and 10'
            }), 400
        
        # Build thesis data
        thesis_data = {
            'user_id': user_id,
            'ticker': data['ticker'].upper().strip(),
            'thesis': data['thesis'].strip(),
            'conviction': conviction,
            'timeframe': data.get('timeframe', '').strip(),
            'target_price': data.get('target_price'),
            'entry_price': data.get('entry_price'),
            'expected_return': data.get('expected_return'),
            'outcome': 'pending',
            'status': 'active',
            'created_at': datetime.utcnow().isoformat(),
            'updated_at': datetime.utcnow().isoformat(),
        }
        
        # Insert thesis
        response = supabase.table('investment_theses')\
            .insert(thesis_data)\
            .execute()
        
        created_thesis = response.data[0]
        
        return jsonify({
            'success': True,
            'data': format_thesis_response(created_thesis),
            'message': 'Thesis created successfully'
        }), 201
        
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500


# Error handlers
@dashboard_bp.errorhandler(404)
def not_found(error):
    return jsonify({
        'success': False,
        'error': 'Resource not found'
    }), 404


@dashboard_bp.errorhandler(500)
def internal_error(error):
    return jsonify({
        'success': False,
        'error': 'Internal server error'
    }), 500

