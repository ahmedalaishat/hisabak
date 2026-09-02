// One-time codegen: Hugeicons (free, MIT) stroke paths -> Compose ImageVector Kotlin.
//
// Usage (regenerate shared/.../ui/icons/HugeIcons.kt after editing the maps below):
//   npm i --no-save @hugeicons/core-free-icons && node tools/gen-hugeicons.mjs
//
// Sources:
//   - HGI: design skill hugeicons-glyphs.js  (window.HGI, keyed by Material symbol name)
//   - HG : design skill hugeicons-data.js     (window.HG, keyed by category concept)
//   - gaps: @hugeicons/core-free-icons          (icons not vendored in the skill)
import { readFileSync, writeFileSync } from 'node:fs';
import vm from 'node:vm';
import { categoryIcons, categoryIconGroups } from './category-icons.mjs';

import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SKILL = resolve(ROOT, '.claude/skills/hisabak-design/ui_kits');
const OUT = resolve(ROOT, 'shared/src/commonMain/kotlin/com/hisabak/ui/icons/HugeIcons.kt');
const OUT_VECTORS = resolve(ROOT, 'shared/src/commonMain/kotlin/com/hisabak/ui/icons/CategoryIconVectors.kt');
const OUT_CATALOG = resolve(ROOT, 'shared/src/commonMain/kotlin/com/hisabak/feature/category/domain/CategoryIconCatalog.kt');

function loadWindow(file) {
  const sandbox = { window: {} };
  vm.createContext(sandbox);
  vm.runInContext(readFileSync(file, 'utf8'), sandbox);
  return sandbox.window;
}
const HGI = loadWindow(`${SKILL}/mobile/hugeicons-glyphs.js`).HGI;     // { name: [d, d, ...] }
const HG = loadWindow(`${SKILL}/hugeicons-data.js`).HG;               // { key: [["path",{d}], ...] }
const free = await import('@hugeicons/core-free-icons');

// circle/line/rect/polyline -> path "d" so every icon is pure path data.
function elementToD(tag, a) {
  if (tag === 'path') return a.d;
  if (tag === 'circle') {
    const cx = +a.cx, cy = +a.cy, r = +a.r;
    return `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`;
  }
  if (tag === 'ellipse') {
    const cx = +a.cx, cy = +a.cy, rx = +a.rx, ry = +a.ry;
    return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${2 * rx} 0a${rx} ${ry} 0 1 0 ${-2 * rx} 0`;
  }
  if (tag === 'line') return `M${a.x1} ${a.y1}L${a.x2} ${a.y2}`;
  if (tag === 'rect') {
    const x = +a.x, y = +a.y, w = +a.width, h = +a.height;
    return `M${x} ${y}h${w}v${h}h${-w}z`;
  }
  if (tag === 'polyline' || tag === 'polygon') {
    const pts = a.points.trim().split(/\s+|,/).map(Number);
    let d = `M${pts[0]} ${pts[1]}`;
    for (let i = 2; i < pts.length; i += 2) d += `L${pts[i]} ${pts[i + 1]}`;
    if (tag === 'polygon') d += 'z';
    return d;
  }
  throw new Error('unsupported tag ' + tag);
}
const freePaths = (exportName) => {
  const data = free[exportName];
  if (!data) throw new Error('missing free icon ' + exportName);
  return data.map(([tag, a]) => elementToD(tag, a));
};

// app icon (PascalCase, mirrors Icons.* leaf) -> HGI key
const chrome = {
  AccountBalanceWallet: 'account_balance_wallet', Add: 'add', ArrowBack: 'arrow_back',
  Bolt: 'bolt', CalendarToday: 'calendar_today', CardGiftcard: 'card_giftcard',
  Category: 'category', Check: 'check', CheckCircle: 'check_circle', ChevronRight: 'chevron_right',
  CloudDownload: 'cloud_download', CloudOff: 'cloud_off', CloudSync: 'cloud_sync',
  CloudUpload: 'cloud_upload', DirectionsCar: 'directions_car', Download: 'download',
  ErrorOutline: 'error', ExpandLess: 'expand_less', ExpandMore: 'expand_more',
  Favorite: 'favorite', FlightTakeoff: 'flight_takeoff', Home: 'home', Insights: 'insights',
  Key: 'key', KeyboardArrowRight: 'chevron_right', Lock: 'lock', MenuBook: 'menu_book',
  Movie: 'movie', Notifications: 'notifications', NotificationsActive: 'notifications',
  NotificationsNone: 'notifications_none', Palette: 'palette', PriorityHigh: 'priority_high',
  Translate: 'translate',
  ReceiptLong: 'receipt_long', Restaurant: 'restaurant', Savings: 'savings',
  Schedule: 'schedule', Search: 'search', Settings: 'settings', ShoppingCart: 'shopping_cart',
  Sms: 'sms', SpaceDashboard: 'dashboard', Star: 'star', Storefront: 'storefront',
  TrendingDown: 'trending_down', TrendingUp: 'trending_up', Work: 'work',
};
// gaps not in the vendored set -> @hugeicons/core-free-icons export
const gaps = {
  ArrowUpward: 'ArrowUp01Icon', ArrowDownward: 'ArrowDown01Icon', Circle: 'CircleIcon',
  DeleteOutline: 'Delete02Icon', Inbox: 'InboxIcon', Layers: 'Layers01Icon',
  List: 'LeftToRightListBulletIcon', Message: 'Message01Icon', PhoneAndroid: 'SmartPhone01Icon',
  VisibilityOff: 'ViewOffIcon', Close: 'Cancel01Icon', MoreVert: 'MoreVerticalIcon',
};
// category concept key -> HG key (Kotlin val name)
const categories = {
  Wallet: 'wallet', Cart: 'cart', Briefcase: 'briefcase', Car: 'car', Utensils: 'utensils',
  PiggyBank: 'piggy-bank', Film: 'film', Book: 'book', Heart: 'heart', Gift: 'gift', Plane: 'plane',
};
// directional icons that should flip in RTL (Arabic), matching Material's AutoMirrored.*
const autoMirror = new Set(['ArrowBack', 'ChevronRight', 'KeyboardArrowRight', 'List', 'Message']);

const icons = {}; // name -> { paths: [d], mirror: bool }
for (const [name, key] of Object.entries(chrome)) {
  if (!HGI[key]) throw new Error('HGI missing ' + key);
  icons[name] = { paths: HGI[key], mirror: autoMirror.has(name) };
}
for (const [name, ex] of Object.entries(gaps)) {
  icons[name] = { paths: freePaths(ex), mirror: autoMirror.has(name) };
}
for (const [name, key] of Object.entries(categories)) {
  if (!HG[key]) throw new Error('HG missing ' + key);
  icons[name] = { paths: HG[key].map(([tag, a]) => elementToD(tag, a)), mirror: false };
}

// Category-picker icons: `app` entries reuse a vector already generated above, `free` entries
// add one named after the key (IceCream for "ice-cream").
const pascal = (key) => key.split('-').map((p) => p[0].toUpperCase() + p.slice(1)).join('');
const vectorNameForKey = {};
for (const entry of categoryIcons) {
  if (entry.src.app) {
    if (!icons[entry.src.app]) throw new Error(`${entry.key}: app icon ${entry.src.app} not generated`);
    vectorNameForKey[entry.key] = entry.src.app;
    continue;
  }
  const name = pascal(entry.key);
  if (icons[name] && !vectorNameForKey[entry.key]) throw new Error(`${entry.key}: vector name ${name} already taken`);
  icons[name] = { paths: freePaths(entry.src.free), mirror: false };
  vectorNameForKey[entry.key] = name;
}

const names = Object.keys(icons).sort();
const lines = names.map((n) => {
  const { paths, mirror } = icons[n];
  const args = [...paths.map((d) => `"${d}"`), mirror ? 'autoMirror = true' : null]
    .filter(Boolean).join(',\n        ');
  return `    val ${n}: ImageVector by lazy(LazyThreadSafetyMode.NONE) {\n        strokeIcon(\n        ${args},\n        )\n    }`;
});

const file = `package com.hisabak.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hugeicons (free, MIT) stroke icons as Compose [ImageVector]s — the app's icon set.
 *
 * GENERATED by tools/gen-hugeicons.mjs from the design skill's vendored Hugeicons data
 * (\`ui_kits/mobile/hugeicons-glyphs.js\`, \`ui_kits/hugeicons-data.js\`) plus a few icons pulled
 * from \`@hugeicons/core-free-icons\`. Do not edit by hand — re-run the generator. Each icon is a
 * 24×24 stroke glyph (width 1.5, round caps/joins); the template stroke colour is overridden by
 * \`Icon(tint = …)\`, exactly like the Material vectors it replaces. Directional icons set
 * \`autoMirror\` so they flip under RTL (Arabic), matching the old \`Icons.AutoMirrored.*\`.
 */
object HugeIcons {
${lines.join('\n')}
}

private fun strokeIcon(vararg paths: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        for (p in paths) {
            addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
`;

writeFileSync(OUT, file);

const GENERATED = (from) => `/*
 * GENERATED by tools/gen-hugeicons.mjs from ${from}. Do not edit by hand — re-run the generator.
 */`;

const groupEnum = categoryIconGroups.map((g) => g.replace(/-/g, '_').toUpperCase());
const entryLines = categoryIcons.map((e) => {
  const kw = [...e.en, ...e.ar].map((k) => `"${k}"`).join(', ');
  return `        CategoryIconEntry("${e.key}", CategoryIconGroup.${e.group.replace(/-/g, '_').toUpperCase()}, listOf(${kw})),`;
});
const vectorLines = categoryIcons.map(
  (e) => `        "${e.key}" -> HugeIcons.${vectorNameForKey[e.key]}`,
);

writeFileSync(OUT_VECTORS, `package com.hisabak.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

${GENERATED('tools/category-icons.mjs')}

/**
 * The drawable half of the category icon catalogue. The keys, groups, and search keywords live in
 * \`feature/category/domain/CategoryIconCatalog\` so domain code can reason about icons without
 * reaching into presentation; this maps a key to the glyph that draws it.
 */
fun categoryIconVector(key: String?): ImageVector? = when (key) {
${vectorLines.join('\n')}
    else -> null
}
`);

writeFileSync(OUT_CATALOG, `package com.hisabak.feature.category.domain

${GENERATED('tools/category-icons.mjs')}

/** A section of the icon picker. Header text comes from \`category_icon_group_*\` string resources. */
enum class CategoryIconGroup {
    ${groupEnum.join(',\n    ')},
}

/**
 * One pickable category icon. [keywords] holds English *and* Arabic terms — both are matched
 * whatever the app language, so search works for either.
 */
data class CategoryIconEntry(
    val key: String,
    val group: CategoryIconGroup,
    val keywords: List<String>,
)

/**
 * Every icon a category can use, in picker order. Keys are persisted on Category rows and in
 * backups, so this list is **append-only** — never rename or remove an entry.
 */
object CategoryIconCatalog {

    val entries: List<CategoryIconEntry> = listOf(
${entryLines.join('\n')}
    )

    val keys: List<String> = entries.map { it.key }
}
`);

console.log('wrote', OUT, '\nicons:', names.length, '\ncategory icons:', categoryIcons.length);
