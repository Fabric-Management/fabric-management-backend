# 📚 Documentation Principles

**Last Updated:** October 10, 2025  
**Status:** ✅ MANDATORY - Apply to all documentation  
**Purpose:** Clear rules for documentation structure and usage

---

## 🎯 CORE PRINCIPLES

### 1. README = FİHRİST (Index)

```
✅ README.md files are INDEXES, not content!

DO:
- List documents with descriptions
- Provide quick navigation tables
- Include priority/time estimates
- Link to actual content

DON'T:
- Write detailed content in READMEs (max 50 lines explanation)
- Duplicate information from other docs
- Create long READMEs (keep under 200 lines)
```

### 2. NO CONTENT LOSS

```
✅ When reorganizing:

BEFORE deleting/moving:
- Check if content exists elsewhere
- If unique → move to appropriate file
- If duplicate → keep most recent/accurate version
- If outdated → archive to docs/reports/archive/

NEVER:
- Delete unique information
- Remove working solutions
- Discard valuable examples
```

### 3. SINGLE SOURCE OF TRUTH

```
✅ Each information has ONE primary location

Examples:
- System architecture → ARCHITECTURE.md
- Coding principles → development/PRINCIPLES.md
- API patterns → development/MICROSERVICES_API_STANDARDS.md
- UUID standards → development/DATA_TYPES_STANDARDS.md
- Security → SECURITY.md

Other docs can REFERENCE, but not duplicate.
```

### 4. CLEAR HIERARCHY

```
docs/
├── Critical docs at root (ARCHITECTURE, SECURITY, AI_LEARNINGS)
├── Category folders (development/, deployment/, etc.)
│   ├── README.md (fihrist)
│   └── Detailed guides
└── Archives (reports/, future/)

Rule: 3 levels max, no deeper nesting.
```

### 5. NAMING CONVENTION

```
✅ USE:
- UPPERCASE.md for main documents
- README.md for indexes (always)
- Descriptive names (GETTING_STARTED.md not START.md)
- Date suffix for reports (REPORT_NAME_OCT_10_2025.md)

DON'T USE:
- lowercase.md (except README.md in special cases)
- Abbreviations (DOC.md, ARCH.md)
- Unclear names (GUIDE.md, INFO.md)
```

---

## 📂 FOLDER STRUCTURE RULES

### Root Level (docs/)

**ONLY critical/main documents:**

- AI_ASSISTANT_LEARNINGS.md
- ARCHITECTURE.md
- SECURITY.md
- README.md (main index)

**Everything else → category folders**

### Category Folders

**Required structure:**

```
{category}/
├── README.md           # Fihrist (mandatory)
└── *.md files          # Actual content
```

**Standard categories:**

- `development/` - Development standards & guides
- `deployment/` - Deployment & DevOps guides
- `architecture/` - Architecture references (can be just README → main doc)
- `api/` - API documentation
- `database/` - Database operations
- `services/` - Service-specific docs
- `troubleshooting/` - Problem solving
- `reports/` - Historical reports & analyses
- `future/` - Planned features (not yet implemented)

---

## 📝 DOCUMENT TYPES

### 1. Fihrist (Index) - README.md

**Purpose:** Navigate to actual content  
**Max Length:** 200 lines  
**Must Have:**

- Document index table
- Quick navigation
- Links to related docs

**Example:**

```markdown
# Category Documentation

## 📚 Documentation Index

| Document               | Description | Priority |
| ---------------------- | ----------- | -------- |
| [GUIDE.md](./GUIDE.md) | Main guide  | 🔴 High  |

## 🎯 Quick Navigation

...
```

### 2. Main Document

**Purpose:** Comprehensive guide on a topic  
**Recommended:** 300-800 lines  
**Must Have:**

- Clear table of contents
- Practical examples
- Links to related docs
- Last updated date

### 3. Service README (`/services/{service}/README.md`)

**Purpose:** Quick reference for service  
**Max Length:** 100 lines  
**Must Have:**

- Quick start
- Key features (bullets)
- Port & config
- Link to full docs

### 4. Reports

**Location:** `docs/reports/`  
**Naming:** `TOPIC_TYPE_DATE.md`  
**Archive:** Older than 3 months → `archive/`

---

## 🔍 FINDING INFORMATION

### Navigation Flow

```
1. Start: docs/README.md (main index)
   ↓
2. Find category: development/, deployment/, etc.
   ↓
3. Check category README.md (fihrist)
   ↓
4. Go to specific document
```

### Quick Reference Table (Every README)

```markdown
| Question    | Document | Section      |
| ----------- | -------- | ------------ |
| How do I X? | GUIDE.md | Section Name |
```

---

## ✅ CHECKLIST: Creating/Updating Docs

### Before Creating New Doc

- [ ] Does this information exist elsewhere?
- [ ] Can it be added to existing doc?
- [ ] Is it worth a separate file? (300+ lines or unique topic)
- [ ] Which category does it belong to?

### When Creating Doc

- [ ] Use clear, descriptive filename
- [ ] Add to category README.md (fihrist)
- [ ] Include table of contents (if >100 lines)
- [ ] Add "Last Updated" date
- [ ] Link to related docs
- [ ] Add examples where applicable

### When Updating Doc

- [ ] Update "Last Updated" date
- [ ] Check for duplicates in other docs
- [ ] Update links if structure changed
- [ ] Update category README if title/purpose changed

### When Deleting/Moving Doc

- [ ] Verify content exists elsewhere OR
- [ ] Move unique content to appropriate location
- [ ] Update all links pointing to it
- [ ] Remove from category README

---

## 🚫 ANTI-PATTERNS

### ❌ DON'T DO THIS

```
❌ Long READMEs with detailed content (>300 lines)
   → Split: README (index) + GUIDE.md (content)

❌ Duplicate content in multiple files
   → One source of truth + references

❌ Unclear filenames (doc.md, guide.md, info.md)
   → Use descriptive names (DEPLOYMENT_GUIDE.md)

❌ Deep nesting (docs/a/b/c/d/file.md)
   → Max 3 levels

❌ Empty folders
   → Remove or add content

❌ Mixed naming (some UPPER, some lower)
   → All main docs UPPERCASE.md

❌ Content in wrong category
   → Architecture in deployment/, etc.
```

---

## 🎯 EXAMPLES

### ✅ GOOD Structure

```
docs/development/
├── README.md (150 lines - fihrist)
├── PRINCIPLES.md (985 lines - detailed)
├── GETTING_STARTED.md (400 lines - guide)
└── DATA_TYPES_STANDARDS.md (976 lines - standards)
```

### ❌ BAD Structure

```
docs/development/
├── readme.md (600 lines - too much content!)
├── guide.md (unclear what it is)
├── info.md (unclear what it is)
└── tips/tricks/advanced/GUIDE.md (too deep!)
```

---

## 📊 FILE SIZE GUIDELINES

| Doc Type        | Min | Ideal | Max  | Action if Exceeded                |
| --------------- | --- | ----- | ---- | --------------------------------- |
| **README**      | 50  | 150   | 250  | Split to multiple docs            |
| **Guide**       | 200 | 500   | 1000 | Consider splitting by topic       |
| **Standards**   | 300 | 700   | 1200 | OK if comprehensive               |
| **Service Doc** | 200 | 400   | 800  | Split: Architecture + API + Guide |

---

## 🔄 REVIEW PROCESS

### Monthly Review

- [ ] Check for outdated docs (>6 months since update)
- [ ] Archive completed reports (>3 months old)
- [ ] Verify all links work
- [ ] Update file sizes if grown too large
- [ ] Check for duplicate content

### When Adding Feature

- [ ] Update relevant docs
- [ ] Add to API docs if new endpoint
- [ ] Update ARCHITECTURE.md if structural change
- [ ] Create report if major feature

---

## ⚡ QUICK RULES (Memorize This)

1. **README = Index** (max 200 lines)
2. **One source** of truth per topic
3. **Root = Critical** docs only
4. **UPPERCASE** for main docs
5. **Archive** old reports
6. **No loss** of unique content
7. **Links** always up to date
8. **Date** every document

---

**Enforced By:** All team members  
**Violations:** PR will be rejected  
**Questions:** #fabric-docs on Slack
