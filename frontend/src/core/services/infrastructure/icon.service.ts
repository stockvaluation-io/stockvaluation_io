import { Injectable } from '@angular/core';

// Import all required icons
import {
  lucideSearch, lucideX, lucideMenu, lucideChevronDown, lucideChevronUp,
  lucideChevronLeft, lucideChevronRight, lucideArrowRight, lucideArrowLeft,
  lucideExternalLink, lucideMail, lucidePhone, lucideCircleAlert,
  lucideTriangleAlert, lucideCircleCheck, lucideInfo, lucideLoader,
  lucideRefreshCw, lucideTwitter, lucideLinkedin, lucideFileText,
  lucideBookOpen, lucideClock, lucideShieldCheck, lucideTrendingUp,
  lucideChartBar, lucideLock, lucideTarget, lucideEyeOff, lucideDatabase,
  lucideUserCheck, lucideCookie, lucideGlobe, lucidePencil, lucidePlay,
  lucideSettings, lucideDollarSign, lucideQuote, lucideMapPin, lucideSun,
  lucideMoon, lucideUser
} from '@ng-icons/lucide';
import { 
  heroEnvelope, heroExclamationCircle, heroInformationCircle, heroShieldCheck,
  heroGlobeAmericas, heroUserGroup, heroCog6Tooth, heroDocumentText,
  heroPlayCircle, heroQuestionMarkCircle, heroHome
} from '@ng-icons/heroicons/outline';
import { 
  heroEnvelopeSolid
} from '@ng-icons/heroicons/solid';
import { 
  tablerBrandX, tablerDatabase, tablerCookie, tablerShieldCheck, 
  tablerUsers, tablerWorld, tablerEdit, tablerInfoCircle, tablerTarget,
  tablerMessageCircle, tablerSend, tablerChartLine, tablerPlayerPlay,
  tablerTools, tablerCurrencyDollar, tablerHelp, tablerBrandMedium
} from '@ng-icons/tabler-icons';
import { hugeTick01, hugeCancelCircle, hugeThumbsUp, hugeThumbsDown, hugeQuoteUp, hugeQuoteDown } from '@ng-icons/huge-icons';

@Injectable({
  providedIn: 'root'
})
export class IconService {
  
  // Icon sizing standards
  readonly sizes = {
    xs: '10',
    sm: '14', 
    md: '16',
    lg: '20',
    xl: '24',
    '2xl': '32'
  } as const;

  // All available icons for ng-icons
  static readonly allIcons = {
    // Lucide icons
    lucideSearch, lucideX, lucideMenu, lucideChevronDown, lucideChevronUp,
    lucideChevronLeft, lucideChevronRight, lucideArrowRight, lucideArrowLeft,
    lucideExternalLink, lucideMail, lucidePhone, lucideCircleAlert,
    lucideTriangleAlert, lucideCircleCheck, lucideInfo, lucideLoader,
    lucideRefreshCw, lucideTwitter, lucideLinkedin, lucideFileText,
    lucideBookOpen, lucideClock, lucideShieldCheck, lucideTrendingUp,
    lucideChartBar, lucideLock, lucideTarget, lucideEyeOff, lucideDatabase,
    lucideUserCheck, lucideCookie, lucideGlobe, lucidePencil, lucidePlay,
    lucideSettings, lucideDollarSign, lucideQuote, lucideMapPin, lucideSun,
    lucideMoon, lucideUser,
    // Heroicons outline
    heroEnvelope, heroExclamationCircle, heroInformationCircle, heroShieldCheck,
    heroGlobeAmericas, heroUserGroup, heroCog6Tooth, heroDocumentText,
    heroPlayCircle, heroQuestionMarkCircle, heroHome,
    // Heroicons solid
    heroEnvelopeSolid,
    // Tabler icons
    tablerBrandX, tablerDatabase, tablerCookie, tablerShieldCheck, 
    tablerUsers, tablerWorld, tablerEdit, tablerInfoCircle, tablerTarget,
    tablerMessageCircle, tablerSend, tablerChartLine, tablerPlayerPlay,
    tablerTools, tablerCurrencyDollar, tablerHelp, tablerBrandMedium,
    // Huge icons
    hugeTick01, hugeCancelCircle, hugeThumbsUp, hugeThumbsDown, hugeQuoteUp, hugeQuoteDown
  };

  // Common icon mappings for consistent usage
  readonly icons = {
    // Navigation & UI
    search: 'lucideSearch',
    close: 'lucideX',
    cancel: 'hugeCancelCircle',
    menu: 'lucideMenu',
    chevronDown: 'lucideChevronDown',
    chevronUp: 'lucideChevronUp',
    chevronLeft: 'lucideChevronLeft',
    chevronRight: 'lucideChevronRight',
    arrowRight: 'lucideArrowRight',
    arrowLeft: 'lucideArrowLeft',
    'arrow-right': 'lucideArrowRight',
    'arrow-left': 'lucideArrowLeft',
    externalLink: 'lucideExternalLink',
    home: 'heroHome',
    
    // Communication
    mail: 'lucideMail',
    mailFilled: 'heroEnvelopeSolid',
    phone: 'lucidePhone',
    message: 'tablerMessageCircle',
    send: 'tablerSend',
    
    // Status & Feedback
    error: 'lucideCircleAlert',
    warning: 'lucideTriangleAlert',
    success: 'lucideCircleCheck',
    tick: 'hugeTick01',
    checkmark: 'hugeTick01',
    thumbsUp: 'hugeThumbsUp',
    thumbsDown: 'hugeThumbsDown',
    info: 'lucideInfo',
    loading: 'lucideLoader',
    refresh: 'lucideRefreshCw',
    
    // Social Media
    twitter: 'lucideTwitter', // Keeping for backward compatibility
    x: 'tablerBrandX', // New X logo from Tabler
    linkedin: 'lucideLinkedin',
    medium: 'tablerBrandMedium',
    
    // Content & Documents
    document: 'lucideFileText',
    blog: 'lucideBookOpen',
    
    // Time & Calendar
    clock: 'lucideClock',
    
    // Security & Shield
    shield: 'lucideShieldCheck',
    lock: 'lucideLock',
    privacy: 'lucideEyeOff',
    
    // Business & Charts
    chart: 'lucideTrendingUp',
    analytics: 'lucideChartBar',
    target: 'lucideTarget',
    
    // Actions & Controls
    play: 'lucidePlay',
    help: 'tablerHelp',
    quote: 'lucideQuote',
    quoteUp: 'hugeQuoteUp',
    quoteDown: 'hugeQuoteDown',
    settings: 'lucideSettings',
    
    // Theme toggle
    sun: 'lucideSun',
    moon: 'lucideMoon',
    
    // Data & Information (mixed libraries for variety)
    database: 'tablerDatabase',
    userCheck: 'tablerUsers', 
    user: 'lucideUser',
    cookie: 'tablerCookie',
    globe: 'heroGlobeAmericas',
    mapPin: 'lucideMapPin',
    edit: 'tablerEdit',
    pencil: 'lucidePencil',
    
    // Privacy specific icons with variety
    tablerInfo: 'tablerInfoCircle',
    heroShield: 'heroShieldCheck',
    heroSettings: 'heroCog6Tooth',
    heroDocument: 'heroDocumentText',
    tablerShield: 'tablerShieldCheck',
    tablerTarget: 'tablerTarget',
    
    // FAQ and Help icons with variety
    faqAll: 'tablerChartLine',
    faqStart: 'heroPlayCircle', 
    faqPlatform: 'tablerTools',
    faqValuation: 'tablerCurrencyDollar',
    faqSupport: 'tablerHelp',
    
    // Alternative icons using Heroicons for variety  
    heroMail: 'heroEnvelope',
    heroError: 'heroExclamationCircle',
    heroInfo: 'heroInformationCircle',
    heroUsers: 'heroUserGroup',
    heroQuestion: 'heroQuestionMarkCircle'
  } as const;

  /**
   * Get icon name with fallback
   */
  getIcon(iconKey: keyof typeof this.icons): string {
    return this.icons[iconKey] || 'lucideInfo';
  }

  /**
   * Get size value with fallback
   */
  getSize(sizeKey: keyof typeof this.sizes): string {
    return this.sizes[sizeKey] || this.sizes.lg;
  }
}