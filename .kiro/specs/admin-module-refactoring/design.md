# Admin Module Refactoring - Design Document

## Overview

This design document outlines the refactoring of the admin module for the Newsletter AI Processor system. The current admin interface has grown organically and now suffers from several issues:

- **Monolithic pages**: The recommendations.html page contains multiple unrelated features (keyword correction, content recommendations, user archive management)
- **Code duplication**: Similar JavaScript patterns and UI components are repeated across pages
- **Inconsistent UX**: Each page has different styling and interaction patterns
- **Poor maintainability**: Complex nested functionality makes it difficult to modify or extend

The refactoring will focus on:
1. Removing unused recommendations page
2. Creating a shared component system for consistent UI/UX
3. Enhancing user information viewing capabilities
4. Improving navigation and user workflows

## Architecture

### High-Level Structure

```
admin/
├── src/main/resources/
│   ├── static/
│   │   ├── css/
│   │   │   └── admin-common.css          # Shared styles
│   │   └── js/
│   │       ├── components/               # Reusable UI components
│   │       │   ├── modal.js
│   │       │   ├── toast.js
│   │       │   └── content-card.js
│   │       └── utils/                    # Shared utilities
│   │           ├── api-client.js
│   │           └── ui-helpers.js
│   └── templates/
│       ├── components/                   # Thymeleaf fragments
│       │   ├── header.html
│       │   └── navigation.html
│       ├── contents.html                 # Content management (existing)
│       ├── keywords.html                 # Keyword management (existing)
│       ├── exposure-fix.html             # Keyword correction (existing)
│       ├── users.html                    # NEW: Enhanced user management
│       ├── newsletter-sources.html       # Newsletter source management (existing)
│       └── rss-reader.html               # RSS feed reader (existing)
```

### Page Responsibilities

**Removed:**
- `recommendations.html`: Removed entirely (rarely used)

**Enhanced:**
- `users.html`: NEW comprehensive user management page with:
  - User search and listing
  - User detail view (preferences, categories, device token)
  - Daily archive viewing and creation
  - User activity history
  - Category preference management

### Component System

**Shared Components (JavaScript):**
- `ContentCard`: Reusable content display component
- `Modal`: Generic modal dialog system
- `Toast`: Notification system
- `KeywordBadge`: Keyword display with weight
- `ApiClient`: Centralized API communication

**Shared Styles:**
- Consistent color scheme and spacing
- Reusable CSS classes for common patterns
- Responsive grid system

## Components and Interfaces

### 1. Enhanced User Management Page

**Purpose**: Comprehensive user information viewing and management with statistics

**Key Features:**
- **User Search**: Search by user ID or device token
- **User List**: Paginated list of all users with basic info
- **User Statistics Dashboard**:
  - Total active users (users with at least one archive)
  - Most active users (top 10 by archive count)
  - User activity trend graph (last 30 days)
  - Daily active users chart
- **User Detail View**:
  - Basic info (ID, device token, creation date)
  - Category preferences with weights
  - Activity statistics:
    - Total days active (archive count)
    - Last active date
    - Activity streak (consecutive days)
    - Activity timeline graph (last 30 days)
- **Daily Archive Management**:
  - View user's today archive
  - Create new archive for user
  - View archive history (last 30 days with calendar view)
  - Display exposure content in archive
- **Category Preference Editor**:
  - View user's selected categories
  - See category weights/priorities
  - Quick link to category management

**API Endpoints:**
- `GET /api/users` - List all users (paginated)
- `GET /api/users/statistics` - Get overall user statistics
- `GET /api/users/most-active` - Get most active users (top 10)
- `GET /api/users/activity-trend` - Get daily active user counts (last 30 days)
- `GET /api/users/{userId}` - Get user details
- `GET /api/users/{userId}/statistics` - Get user-specific statistics
- `GET /api/users/{userId}/categories` - Get user's category preferences
- `GET /api/users/{userId}/archives` - Get user's archive history (last 30 days)
- `GET /api/recommendations/users/{userId}/today-archive` - Get today's archive
- `POST /api/recommendations/users/{userId}/today-archive` - Create today's archive

### 2. Exposure Fix Page (Existing)

**Purpose**: Correct exposure content with "No Keywords"

**Current State**: Already exists and works well
**Enhancement**: Use shared components for consistency

### 3. Shared Component Library

**ContentCard Component:**
```javascript
class ContentCard {
  constructor(content, options = {}) {
    this.content = content;
    this.showSummary = options.showSummary ?? true;
    this.showKeywords = options.showKeywords ?? true;
    this.actions = options.actions ?? [];
  }
  
  render() {
    // Returns DOM element
  }
}
```

**Modal Component:**
```javascript
class Modal {
  constructor(title, content) {
    this.title = title;
    this.content = content;
  }
  
  show() { /* ... */ }
  hide() { /* ... */ }
  setContent(content) { /* ... */ }
}
```

**UserCard Component:**
```javascript
class UserCard {
  constructor(user, options = {}) {
    this.user = user;
    this.showDetails = options.showDetails ?? false;
    this.showStatistics = options.showStatistics ?? false;
    this.actions = options.actions ?? [];
  }
  
  render() {
    // Returns DOM element with user info and optional statistics
  }
}
```

**Chart Component:**
```javascript
class ActivityChart {
  constructor(containerId, data, options = {}) {
    this.containerId = containerId;
    this.data = data;
    this.chartType = options.chartType ?? 'line'; // line, bar, calendar
    this.title = options.title ?? '';
  }
  
  render() {
    // Renders chart using Chart.js or similar library
  }
  
  update(newData) {
    // Updates chart with new data
  }
}
```

**ApiClient:**
```javascript
class ApiClient {
  static async get(url) { /* ... */ }
  static async post(url, data) { /* ... */ }
  static async put(url, data) { /* ... */ }
  static async delete(url) { /* ... */ }
}
```

## Data Models

### Frontend Data Structures

**User:**
```typescript
interface User {
  id: number;
  deviceToken: string;
  createdAt: string;
  updatedAt: string;
}
```

**UserCategory:**
```typescript
interface UserCategory {
  categoryId: number;
  categoryName: string;
  weight: number;
}
```

**UserArchive:**
```typescript
interface UserArchive {
  userId: number;
  date: string;
  exposureContents: ExposureContent[];
  exists: boolean;
}
```

**UserStatistics:**
```typescript
interface UserStatistics {
  totalDaysActive: number;
  lastActiveDate: string;
  activityStreak: number;
  activityTimeline: ActivityDay[];
}

interface ActivityDay {
  date: string;
  hasArchive: boolean;
  contentCount: number;
}
```

**OverallStatistics:**
```typescript
interface OverallStatistics {
  totalUsers: number;
  activeUsers: number;
  dailyActiveUsers: DailyActiveUser[];
  mostActiveUsers: MostActiveUser[];
}

interface DailyActiveUser {
  date: string;
  count: number;
}

interface MostActiveUser {
  userId: number;
  deviceToken: string;
  archiveCount: number;
  lastActiveDate: string;
}
```

**ExposureContent:**
```typescript
interface ExposureContent {
  id: number;
  contentId: number;
  title: string;
  provocativeKeyword: string;
  provocativeHeadline: string;
  summaryContent: string;
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Page Navigation Consistency
*For any* navigation action from any admin page, the user should always be able to access all other admin pages through the navigation menu.

**Validates: Requirements 8.1, 8.2, 8.4**

### Property 2: Component Reusability
*For any* content card displayed across different pages, the visual presentation and interaction patterns should be identical.

**Validates: Requirements 6.1, 6.7**

### Property 3: User Data Display Consistency
*For any* user information displayed (in list view or detail view), the data should be fetched from the same API endpoints and formatted consistently.

**Validates: Requirements 2.2, 4.2, 7.1, 7.5**

## Migration Strategy

### Phase 1: Create Shared Components
1. Extract common CSS to `admin-common.css`
2. Create JavaScript component library (ContentCard, Modal, Toast, UserCard, ApiClient)
3. Create Thymeleaf header/navigation fragments

### Phase 2: Create Enhanced User Management Page
1. Create `users.html` with user search and listing
2. Implement overall statistics dashboard with charts
3. Implement user detail view with category preferences
4. Add user-specific activity statistics and timeline graph
5. Add daily archive management features
6. Integrate archive history viewing with calendar visualization

### Phase 3: Update Navigation and Remove Recommendations
1. Update navigation menu to include new Users page
2. Remove recommendations.html and related routes
3. Update AdminController to remove recommendations endpoint

### Phase 4: Enhance Existing Pages
1. Update `exposure-fix.html` to use shared components
2. Update other pages to use shared components for consistency

## Implementation Notes

### New API Endpoints Needed

The following API endpoints need to be created in the backend:

```kotlin
// UserApiController.kt
@GetMapping("/api/users")
fun getAllUsers(pageable: Pageable): ResponseEntity<Page<User>>

@GetMapping("/api/users/statistics")
fun getOverallStatistics(): ResponseEntity<OverallStatisticsResponse>

@GetMapping("/api/users/most-active")
fun getMostActiveUsers(
    @RequestParam(defaultValue = "10") limit: Int
): ResponseEntity<List<MostActiveUserResponse>>

@GetMapping("/api/users/activity-trend")
fun getActivityTrend(
    @RequestParam(defaultValue = "30") days: Int
): ResponseEntity<List<DailyActiveUserResponse>>

@GetMapping("/api/users/{userId}")
fun getUserById(@PathVariable userId: Long): ResponseEntity<UserDetailResponse>

@GetMapping("/api/users/{userId}/statistics")
fun getUserStatistics(@PathVariable userId: Long): ResponseEntity<UserStatisticsResponse>

@GetMapping("/api/users/{userId}/categories")
fun getUserCategories(@PathVariable userId: Long): ResponseEntity<List<UserCategoryResponse>>

@GetMapping("/api/users/{userId}/archives")
fun getUserArchiveHistory(
    @PathVariable userId: Long,
    @RequestParam(defaultValue = "30") days: Int
): ResponseEntity<List<ArchiveHistoryResponse>>
```

### Chart Library

Use **Chart.js** for rendering graphs:
- Line chart for activity trends
- Bar chart for daily active users
- Calendar heatmap for user activity timeline (using custom implementation or library like cal-heatmap)

### Data Aggregation

Backend should efficiently aggregate data from `daily_content_archive` table:
- Count archives per user for activity statistics
- Calculate consecutive days for streak calculation
- Group by date for trend analysis
- Use database queries with proper indexing for performance

### Performance Considerations

- Lazy load content cards for large lists
- Implement pagination for user lists
- Cache frequently accessed data in session storage
- Use database indexes on `daily_content_archive.user_id` and `daily_content_archive.date` for efficient queries
- Consider caching statistics data with short TTL (5-10 minutes)

### Browser Support

- Target modern browsers (Chrome, Firefox, Safari, Edge)
- Use vanilla JavaScript (no framework dependencies)
- Graceful degradation for older browsers

