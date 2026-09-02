// The category icon catalogue — the single source of truth for what users can pick.
//
// Each entry: { key, group, src, en, ar }
//   key   persisted in the DB and in backups. NEVER rename or remove one — old rows and
//         restored backups reference it, and an unknown key falls back to a plain circle.
//   group section the picker renders it under (header text comes from CMP string resources).
//   src   where the glyph comes from:
//           { free: 'XIcon' }  -> @hugeicons/core-free-icons export
//           { app:  'Name'  }  -> an icon already vendored in HugeIcons.kt (no duplicate paths)
//   en/ar search keywords. Both are matched regardless of app language, so an Arabic user
//         typing English still finds the icon (and vice versa).
export const categoryIcons = [
  // ── Food & drink ──────────────────────────────────────────────────────────
  { key: 'utensils', group: 'food', src: { app: 'Utensils' }, en: ['restaurant', 'dining', 'food', 'eat', 'lunch', 'dinner'], ar: ['مطعم', 'طعام', 'أكل', 'غداء', 'عشاء'] },
  { key: 'coffee', group: 'food', src: { free: 'Coffee01Icon' }, en: ['coffee', 'cafe', 'latte', 'espresso'], ar: ['قهوة', 'كافيه', 'مقهى'] },
  { key: 'tea', group: 'food', src: { free: 'TeaIcon' }, en: ['tea', 'karak'], ar: ['شاي', 'كرك'] },
  { key: 'pizza', group: 'food', src: { free: 'Pizza02Icon' }, en: ['pizza', 'italian'], ar: ['بيتزا'] },
  { key: 'fast-food', group: 'food', src: { free: 'FrenchFries01Icon' }, en: ['fast food', 'fries', 'burger', 'takeaway'], ar: ['وجبات سريعة', 'بطاطس', 'برجر'] },
  { key: 'bread', group: 'food', src: { free: 'BreadIcon' }, en: ['bread', 'bakery'], ar: ['خبز', 'مخبز'] },
  { key: 'cake', group: 'food', src: { free: 'CakeIcon' }, en: ['cake', 'dessert', 'sweets', 'birthday'], ar: ['كيك', 'حلويات', 'كعكة'] },
  { key: 'ice-cream', group: 'food', src: { free: 'IceCream01Icon' }, en: ['ice cream', 'dessert', 'gelato'], ar: ['آيس كريم', 'مثلجات', 'بوظة'] },
  { key: 'sushi', group: 'food', src: { free: 'SushiIcon' }, en: ['sushi', 'japanese', 'seafood'], ar: ['سوشي', 'ياباني'] },
  { key: 'noodles', group: 'food', src: { free: 'NoodlesIcon' }, en: ['noodles', 'pasta', 'asian', 'ramen'], ar: ['نودلز', 'معكرونة', 'مكرونة'] },
  { key: 'steak', group: 'food', src: { free: 'SteakIcon' }, en: ['steak', 'meat', 'grill', 'bbq'], ar: ['لحم', 'ستيك', 'مشويات', 'شواء'] },
  { key: 'milk', group: 'food', src: { free: 'MilkIcon' }, en: ['milk', 'dairy'], ar: ['حليب', 'ألبان'] },
  { key: 'soft-drink', group: 'food', src: { free: 'SodaCanIcon' }, en: ['soda', 'drink', 'cola', 'juice'], ar: ['مشروب', 'صودا', 'كولا', 'عصير'] },
  { key: 'fruit', group: 'food', src: { free: 'AppleIcon' }, en: ['fruit', 'apple', 'produce'], ar: ['فاكهة', 'تفاح', 'فواكه'] },
  { key: 'vegetables', group: 'food', src: { free: 'BroccoliIcon' }, en: ['vegetables', 'greens', 'produce'], ar: ['خضار', 'خضروات'] },
  { key: 'groceries', group: 'food', src: { free: 'ShoppingBasket01Icon' }, en: ['groceries', 'supermarket', 'basket'], ar: ['بقالة', 'سوبرماركت', 'مقاضي'] },
  { key: 'water', group: 'food', src: { free: 'DropletIcon' }, en: ['water', 'drinking water', 'bill'], ar: ['ماء', 'مياه', 'فاتورة'] },
  { key: 'chef', group: 'food', src: { free: 'ChefHatIcon' }, en: ['cooking', 'chef', 'catering'], ar: ['طبخ', 'شيف', 'ضيافة'] },

  // ── Transport ─────────────────────────────────────────────────────────────
  { key: 'car', group: 'transport', src: { app: 'Car' }, en: ['car', 'vehicle', 'driving', 'auto'], ar: ['سيارة', 'مركبة', 'قيادة'] },
  { key: 'fuel', group: 'transport', src: { free: 'Fuel01Icon' }, en: ['fuel', 'petrol', 'gas', 'station', 'diesel'], ar: ['وقود', 'بنزين', 'محطة', 'ديزل'] },
  { key: 'bus', group: 'transport', src: { free: 'Bus01Icon' }, en: ['bus', 'public transport', 'commute'], ar: ['باص', 'حافلة', 'نقل عام'] },
  { key: 'taxi', group: 'transport', src: { free: 'TaxiIcon' }, en: ['taxi', 'cab', 'ride', 'uber', 'careem'], ar: ['تاكسي', 'أجرة', 'توصيلة'] },
  { key: 'bicycle', group: 'transport', src: { free: 'Bicycle01Icon' }, en: ['bike', 'bicycle', 'cycling'], ar: ['دراجة', 'دراجة هوائية'] },
  { key: 'motorbike', group: 'transport', src: { free: 'Motorbike01Icon' }, en: ['motorbike', 'motorcycle'], ar: ['دراجة نارية', 'موتور'] },
  { key: 'train', group: 'transport', src: { free: 'Train01Icon' }, en: ['train', 'rail', 'railway'], ar: ['قطار', 'سكة'] },
  { key: 'metro', group: 'transport', src: { free: 'MetroIcon' }, en: ['metro', 'subway', 'tram'], ar: ['مترو', 'قطار أنفاق'] },
  { key: 'parking', group: 'transport', src: { free: 'ParkingAreaCircleIcon' }, en: ['parking', 'valet'], ar: ['موقف', 'مواقف', 'باركن'] },
  { key: 'scooter', group: 'transport', src: { free: 'Scooter01Icon' }, en: ['scooter', 'e-scooter'], ar: ['سكوتر'] },
  { key: 'truck', group: 'transport', src: { free: 'TruckIcon' }, en: ['truck', 'delivery', 'freight', 'shipping'], ar: ['شاحنة', 'توصيل', 'شحن'] },
  { key: 'car-repair', group: 'transport', src: { free: 'RepairIcon' }, en: ['repair', 'garage', 'service', 'mechanic'], ar: ['صيانة', 'ورشة', 'تصليح', 'ميكانيكي'] },
  { key: 'ev-charging', group: 'transport', src: { free: 'BatteryCharging01Icon' }, en: ['charging', 'electric', 'ev'], ar: ['شحن', 'كهربائي'] },

  // ── Shopping ──────────────────────────────────────────────────────────────
  { key: 'cart', group: 'shopping', src: { app: 'Cart' }, en: ['shopping', 'cart', 'supermarket', 'store'], ar: ['تسوق', 'عربة', 'سوبرماركت'] },
  { key: 'shopping-bag', group: 'shopping', src: { free: 'ShoppingBag01Icon' }, en: ['bag', 'shopping', 'retail'], ar: ['حقيبة', 'تسوق', 'شراء'] },
  { key: 'clothes', group: 'shopping', src: { free: 'Shirt01Icon' }, en: ['clothes', 'fashion', 'apparel', 'shirt'], ar: ['ملابس', 'أزياء', 'قميص'] },
  { key: 'shoes', group: 'shopping', src: { free: 'ShoesIcon' }, en: ['shoes', 'footwear', 'sneakers'], ar: ['أحذية', 'حذاء', 'جزمة'] },
  { key: 'jewelry', group: 'shopping', src: { free: 'Diamond01Icon' }, en: ['jewelry', 'gold', 'diamond', 'ring'], ar: ['مجوهرات', 'ذهب', 'ألماس', 'خاتم'] },
  { key: 'watch', group: 'shopping', src: { free: 'Watch01Icon' }, en: ['watch', 'accessories'], ar: ['ساعة', 'إكسسوارات'] },
  { key: 'store', group: 'shopping', src: { free: 'Store01Icon' }, en: ['store', 'shop', 'retail', 'mall'], ar: ['متجر', 'محل', 'مول'] },
  { key: 'discount', group: 'shopping', src: { free: 'Discount01Icon' }, en: ['discount', 'sale', 'offer', 'deal'], ar: ['خصم', 'تخفيض', 'عرض'] },
  { key: 'tag', group: 'shopping', src: { free: 'Tag01Icon' }, en: ['price', 'tag', 'label'], ar: ['سعر', 'وسم', 'بطاقة سعر'] },
  { key: 'furniture', group: 'shopping', src: { free: 'Sofa01Icon' }, en: ['furniture', 'sofa', 'couch', 'home'], ar: ['أثاث', 'كنبة', 'مفروشات'] },
  { key: 'gift', group: 'shopping', src: { app: 'Gift' }, en: ['gift', 'present', 'birthday'], ar: ['هدية', 'هدايا', 'عيد ميلاد'] },
  { key: 'perfume', group: 'shopping', src: { free: 'PerfumeIcon' }, en: ['perfume', 'fragrance', 'cosmetics', 'beauty'], ar: ['عطر', 'عطور', 'تجميل'] },

  // ── Home & bills ──────────────────────────────────────────────────────────
  { key: 'home', group: 'home', src: { app: 'Home' }, en: ['home', 'house', 'rent', 'household'], ar: ['منزل', 'بيت', 'إيجار'] },
  { key: 'electricity', group: 'home', src: { app: 'Bolt' }, en: ['electricity', 'power', 'bill', 'dewa'], ar: ['كهرباء', 'فاتورة', 'طاقة'] },
  { key: 'gas', group: 'home', src: { free: 'GasStoveIcon' }, en: ['gas', 'cooking gas', 'stove'], ar: ['غاز', 'طباخ'] },
  { key: 'internet', group: 'home', src: { free: 'Wifi01Icon' }, en: ['internet', 'wifi', 'broadband', 'bill'], ar: ['إنترنت', 'واي فاي', 'نت'] },
  { key: 'tools', group: 'home', src: { free: 'Wrench01Icon' }, en: ['tools', 'repair', 'maintenance', 'diy'], ar: ['أدوات', 'صيانة', 'تصليح'] },
  { key: 'laundry', group: 'home', src: { free: 'WashingMachineIcon' }, en: ['laundry', 'washing', 'dry cleaning'], ar: ['غسيل', 'مغسلة', 'تنظيف'] },
  { key: 'cleaning', group: 'home', src: { free: 'CleaningBucketIcon' }, en: ['cleaning', 'housekeeping', 'maid'], ar: ['تنظيف', 'نظافة', 'خادمة'] },
  { key: 'plant', group: 'home', src: { free: 'Plant01Icon' }, en: ['plants', 'garden', 'flowers'], ar: ['نبات', 'حديقة', 'زراعة'] },
  { key: 'building', group: 'home', src: { free: 'Building01Icon' }, en: ['building', 'apartment', 'rent', 'property'], ar: ['مبنى', 'شقة', 'عمارة', 'عقار'] },
  { key: 'key', group: 'home', src: { app: 'Key' }, en: ['key', 'rent', 'deposit', 'mortgage'], ar: ['مفتاح', 'إيجار', 'رهن'] },
  { key: 'security', group: 'home', src: { free: 'Shield01Icon' }, en: ['security', 'insurance', 'protection'], ar: ['أمن', 'حماية', 'تأمين'] },
  { key: 'lightbulb', group: 'home', src: { free: 'LightbulbIcon' }, en: ['light', 'bulb', 'utilities'], ar: ['إضاءة', 'لمبة', 'مرافق'] },

  // ── Health ────────────────────────────────────────────────────────────────
  { key: 'heart', group: 'health', src: { app: 'Heart' }, en: ['health', 'heart', 'love', 'wellness'], ar: ['صحة', 'قلب', 'حب'] },
  { key: 'medicine', group: 'health', src: { free: 'Medicine01Icon' }, en: ['medicine', 'pharmacy', 'drugs'], ar: ['دواء', 'صيدلية', 'أدوية'] },
  { key: 'hospital', group: 'health', src: { free: 'Hospital01Icon' }, en: ['hospital', 'clinic', 'emergency'], ar: ['مستشفى', 'عيادة', 'طوارئ'] },
  { key: 'doctor', group: 'health', src: { free: 'StethoscopeIcon' }, en: ['doctor', 'checkup', 'appointment'], ar: ['طبيب', 'دكتور', 'فحص', 'موعد'] },
  { key: 'dentist', group: 'health', src: { free: 'DentalToothIcon' }, en: ['dentist', 'teeth', 'dental'], ar: ['أسنان', 'طبيب أسنان'] },
  { key: 'gym', group: 'health', src: { free: 'Dumbbell01Icon' }, en: ['gym', 'fitness', 'workout', 'training'], ar: ['نادي', 'رياضة', 'لياقة', 'جيم'] },
  { key: 'pills', group: 'health', src: { free: 'PillIcon' }, en: ['pills', 'medication', 'supplements'], ar: ['حبوب', 'أدوية', 'مكملات'] },
  { key: 'glasses', group: 'health', src: { free: 'GlassesIcon' }, en: ['glasses', 'optician', 'eye', 'vision'], ar: ['نظارة', 'بصريات', 'عيون'] },
  { key: 'brain', group: 'health', src: { free: 'Brain01Icon' }, en: ['mental health', 'therapy', 'counselling'], ar: ['نفسية', 'علاج نفسي', 'استشارة'] },
  { key: 'baby', group: 'health', src: { free: 'Baby01Icon' }, en: ['baby', 'childcare', 'nursery', 'kids'], ar: ['طفل', 'رضيع', 'حضانة', 'أطفال'] },
  { key: 'yoga', group: 'health', src: { free: 'Yoga01Icon' }, en: ['yoga', 'wellness', 'meditation'], ar: ['يوغا', 'استرخاء', 'تأمل'] },
  { key: 'swimming', group: 'health', src: { free: 'SwimmingIcon' }, en: ['swimming', 'pool'], ar: ['سباحة', 'مسبح'] },

  // ── Travel ────────────────────────────────────────────────────────────────
  { key: 'plane', group: 'travel', src: { app: 'Plane' }, en: ['flight', 'travel', 'airline', 'airport'], ar: ['طيران', 'سفر', 'رحلة', 'مطار'] },
  { key: 'hotel', group: 'travel', src: { free: 'Hotel01Icon' }, en: ['hotel', 'stay', 'accommodation'], ar: ['فندق', 'إقامة', 'سكن'] },
  { key: 'luggage', group: 'travel', src: { free: 'Luggage01Icon' }, en: ['luggage', 'baggage', 'suitcase'], ar: ['حقائب', 'أمتعة', 'شنطة'] },
  { key: 'passport', group: 'travel', src: { free: 'PassportIcon' }, en: ['passport', 'visa', 'immigration'], ar: ['جواز', 'تأشيرة', 'فيزا'] },
  { key: 'beach', group: 'travel', src: { free: 'BeachIcon' }, en: ['beach', 'vacation', 'holiday', 'resort'], ar: ['شاطئ', 'إجازة', 'منتجع'] },
  { key: 'camping', group: 'travel', src: { free: 'TentIcon' }, en: ['camping', 'tent', 'outdoors', 'desert'], ar: ['تخييم', 'خيمة', 'برية', 'كشتة'] },
  { key: 'ticket', group: 'travel', src: { free: 'Ticket01Icon' }, en: ['ticket', 'booking', 'reservation'], ar: ['تذكرة', 'حجز'] },
  { key: 'location', group: 'travel', src: { free: 'Location01Icon' }, en: ['location', 'map', 'trip', 'places'], ar: ['موقع', 'خريطة', 'رحلة'] },

  // ── Work & education ──────────────────────────────────────────────────────
  { key: 'briefcase', group: 'work', src: { app: 'Briefcase' }, en: ['work', 'business', 'job', 'salary'], ar: ['عمل', 'وظيفة', 'شغل', 'راتب'] },
  { key: 'book', group: 'work', src: { app: 'Book' }, en: ['book', 'education', 'reading', 'study'], ar: ['كتاب', 'قراءة', 'تعليم', 'دراسة'] },
  { key: 'school', group: 'work', src: { free: 'School01Icon' }, en: ['school', 'tuition', 'fees', 'education'], ar: ['مدرسة', 'رسوم', 'تعليم'] },
  { key: 'graduation', group: 'work', src: { free: 'GraduationCapIcon' }, en: ['university', 'college', 'degree', 'graduation'], ar: ['جامعة', 'تخرج', 'شهادة', 'كلية'] },
  { key: 'course', group: 'work', src: { free: 'CourseIcon' }, en: ['course', 'training', 'class', 'workshop'], ar: ['دورة', 'تدريب', 'ورشة'] },
  { key: 'office', group: 'work', src: { free: 'Building03Icon' }, en: ['office', 'workplace', 'coworking'], ar: ['مكتب', 'عمل'] },
  { key: 'laptop', group: 'work', src: { free: 'LaptopIcon' }, en: ['laptop', 'computer', 'equipment'], ar: ['لابتوب', 'حاسوب', 'كمبيوتر'] },
  { key: 'printer', group: 'work', src: { free: 'PrinterIcon' }, en: ['printing', 'stationery', 'copies'], ar: ['طباعة', 'قرطاسية', 'نسخ'] },
  { key: 'pencil', group: 'work', src: { free: 'PencilEdit01Icon' }, en: ['stationery', 'supplies', 'writing'], ar: ['قرطاسية', 'أدوات مكتبية', 'كتابة'] },
  { key: 'teacher', group: 'work', src: { free: 'TeacherIcon' }, en: ['teacher', 'lessons', 'tutoring'], ar: ['معلم', 'دروس', 'تدريس'] },

  // ── Money ─────────────────────────────────────────────────────────────────
  { key: 'wallet', group: 'money', src: { app: 'Wallet' }, en: ['wallet', 'cash', 'money', 'spending'], ar: ['محفظة', 'نقود', 'فلوس', 'مصروف'] },
  { key: 'piggy-bank', group: 'money', src: { app: 'PiggyBank' }, en: ['savings', 'saving', 'deposit'], ar: ['توفير', 'ادخار', 'وديعة'] },
  { key: 'bank', group: 'money', src: { free: 'BankIcon' }, en: ['bank', 'banking', 'account'], ar: ['بنك', 'مصرف', 'حساب'] },
  { key: 'credit-card', group: 'money', src: { free: 'CreditCardIcon' }, en: ['card', 'credit', 'debit', 'payment'], ar: ['بطاقة', 'ائتمان', 'دفع'] },
  { key: 'cash', group: 'money', src: { free: 'Cash01Icon' }, en: ['cash', 'banknote', 'notes'], ar: ['نقد', 'كاش', 'أوراق نقدية'] },
  { key: 'coins', group: 'money', src: { free: 'Coins01Icon' }, en: ['coins', 'change', 'small money'], ar: ['عملات', 'فكة', 'خردة'] },
  { key: 'invoice', group: 'money', src: { free: 'Invoice01Icon' }, en: ['invoice', 'bill', 'statement'], ar: ['فاتورة', 'كشف حساب'] },
  { key: 'receipt', group: 'money', src: { free: 'ReceiptIcon' }, en: ['receipt', 'proof'], ar: ['إيصال', 'وصل'] },
  { key: 'tax', group: 'money', src: { free: 'TaxesIcon' }, en: ['tax', 'vat', 'duty'], ar: ['ضريبة', 'ضرائب', 'رسوم'] },
  { key: 'crypto', group: 'money', src: { free: 'Bitcoin01Icon' }, en: ['crypto', 'bitcoin', 'blockchain'], ar: ['عملات رقمية', 'بيتكوين', 'كريبتو'] },
  { key: 'atm', group: 'money', src: { free: 'Atm01Icon' }, en: ['atm', 'withdrawal', 'cash machine'], ar: ['صراف', 'سحب', 'صراف آلي'] },
  { key: 'charity', group: 'money', src: { free: 'CharityIcon' }, en: ['charity', 'donation', 'zakat', 'giving'], ar: ['تبرع', 'صدقة', 'زكاة', 'خير'] },
  { key: 'chart', group: 'money', src: { free: 'Analytics01Icon' }, en: ['investment', 'growth', 'stocks', 'portfolio'], ar: ['استثمار', 'أسهم', 'نمو', 'محفظة'] },
  { key: 'safe', group: 'money', src: { free: 'SafeBoxIcon' }, en: ['safe', 'vault', 'deposit', 'reserve'], ar: ['خزنة', 'وديعة', 'احتياطي'] },
  { key: 'target', group: 'money', src: { free: 'Target01Icon' }, en: ['goal', 'budget', 'target'], ar: ['هدف', 'ميزانية'] },
  { key: 'money-bag', group: 'money', src: { free: 'MoneyBag01Icon' }, en: ['salary', 'income', 'payroll', 'earnings'], ar: ['راتب', 'دخل', 'أجر'] },
  { key: 'transfer', group: 'money', src: { free: 'MoneyExchange01Icon' }, en: ['transfer', 'remittance', 'send money', 'hawala', 'western union'], ar: ['تحويل', 'حوالة', 'إرسال مال', 'تحويلات'] },
  { key: 'exchange', group: 'money', src: { free: 'Exchange01Icon' }, en: ['currency exchange', 'forex', 'transfer', 'travel money'], ar: ['صرافة', 'عملة', 'تحويل', 'حوالة'] },
  { key: 'send-money', group: 'money', src: { free: 'SendIcon' }, en: ['send', 'outgoing', 'remittance', 'transfer'], ar: ['إرسال', 'صادر', 'تحويل'] },
  { key: 'payment', group: 'money', src: { free: 'Payment01Icon' }, en: ['payment', 'pay', 'bill payment', 'settle'], ar: ['دفع', 'سداد', 'دفعة'] },
  { key: 'transaction', group: 'money', src: { free: 'TransactionIcon' }, en: ['transaction', 'transfer', 'movement'], ar: ['معاملة', 'عملية', 'حركة'] },
  { key: 'global', group: 'money', src: { free: 'GlobeIcon' }, en: ['international', 'abroad', 'overseas', 'global'], ar: ['دولي', 'الخارج', 'عالمي'] },
  { key: 'family', group: 'money', src: { free: 'UserGroupIcon' }, en: ['family', 'support', 'dependents', 'home'], ar: ['عائلة', 'أهل', 'إعالة', 'مصروف البيت'] },

  // ── Leisure ───────────────────────────────────────────────────────────────
  { key: 'film', group: 'leisure', src: { app: 'Film' }, en: ['movies', 'cinema', 'film'], ar: ['أفلام', 'سينما', 'فيلم'] },
  { key: 'music', group: 'leisure', src: { free: 'MusicNote01Icon' }, en: ['music', 'songs', 'spotify'], ar: ['موسيقى', 'أغاني'] },
  { key: 'game', group: 'leisure', src: { free: 'GameController01Icon' }, en: ['games', 'gaming', 'console'], ar: ['ألعاب', 'لعبة', 'قيمنق'] },
  { key: 'football', group: 'leisure', src: { free: 'FootballIcon' }, en: ['football', 'soccer', 'sports'], ar: ['كرة قدم', 'رياضة', 'كورة'] },
  { key: 'basketball', group: 'leisure', src: { free: 'BasketballIcon' }, en: ['basketball', 'sports'], ar: ['كرة سلة', 'رياضة'] },
  { key: 'tennis', group: 'leisure', src: { free: 'TennisBallIcon' }, en: ['tennis', 'padel', 'sports'], ar: ['تنس', 'بادل', 'رياضة'] },
  { key: 'camera', group: 'leisure', src: { free: 'Camera01Icon' }, en: ['photography', 'camera', 'photos'], ar: ['تصوير', 'كاميرا', 'صور'] },
  { key: 'art', group: 'leisure', src: { free: 'PaintBrush01Icon' }, en: ['art', 'painting', 'hobby', 'craft'], ar: ['فن', 'رسم', 'هواية'] },
  { key: 'tv', group: 'leisure', src: { free: 'Tv01Icon' }, en: ['tv', 'streaming', 'netflix', 'subscription'], ar: ['تلفاز', 'اشتراك', 'بث', 'تلفزيون'] },
  { key: 'party', group: 'leisure', src: { free: 'PartyIcon' }, en: ['party', 'celebration', 'event', 'wedding'], ar: ['حفلة', 'مناسبة', 'احتفال', 'عرس'] },
  { key: 'popcorn', group: 'leisure', src: { free: 'PopcornIcon' }, en: ['snacks', 'cinema', 'popcorn'], ar: ['فشار', 'وجبات خفيفة', 'سناكس'] },
  { key: 'mic', group: 'leisure', src: { free: 'Mic01Icon' }, en: ['concert', 'karaoke', 'podcast'], ar: ['حفلة', 'غناء', 'بودكاست'] },
  { key: 'puzzle', group: 'leisure', src: { free: 'PuzzleIcon' }, en: ['hobby', 'puzzle', 'board game'], ar: ['هواية', 'ألغاز', 'ألعاب'] },
  { key: 'dice', group: 'leisure', src: { free: 'DiceFaces01Icon' }, en: ['games', 'board games', 'dice'], ar: ['نرد', 'ألعاب', 'طاولة'] },

  // ── Tech ──────────────────────────────────────────────────────────────────
  { key: 'phone', group: 'tech', src: { app: 'PhoneAndroid' }, en: ['phone', 'mobile', 'bill', 'etisalat', 'du'], ar: ['هاتف', 'جوال', 'موبايل', 'فاتورة'] },
  { key: 'headphones', group: 'tech', src: { free: 'HeadphonesIcon' }, en: ['headphones', 'audio', 'earbuds'], ar: ['سماعات', 'صوتيات'] },
  { key: 'code', group: 'tech', src: { free: 'CodeIcon' }, en: ['software', 'development', 'saas', 'tools'], ar: ['برمجة', 'تطوير', 'برامج'] },
  { key: 'battery', group: 'tech', src: { free: 'BatteryFullIcon' }, en: ['battery', 'power', 'charge'], ar: ['بطارية', 'شحن', 'طاقة'] },
  { key: 'cloud', group: 'tech', src: { app: 'CloudUpload' }, en: ['cloud', 'storage', 'backup', 'subscription'], ar: ['سحابة', 'تخزين', 'نسخ احتياطي', 'اشتراك'] },
  { key: 'rocket', group: 'tech', src: { free: 'Rocket01Icon' }, en: ['startup', 'launch', 'project'], ar: ['إطلاق', 'مشروع', 'شركة ناشئة'] },
  { key: 'video', group: 'tech', src: { free: 'Video01Icon' }, en: ['video', 'streaming', 'youtube'], ar: ['فيديو', 'بث'] },

  // ── Other ─────────────────────────────────────────────────────────────────
  { key: 'star', group: 'other', src: { app: 'Star' }, en: ['favourite', 'star', 'special'], ar: ['مفضل', 'نجمة', 'مميز'] },
  { key: 'bookmark', group: 'other', src: { free: 'Bookmark01Icon' }, en: ['bookmark', 'saved', 'later'], ar: ['علامة', 'محفوظ', 'لاحقا'] },
  { key: 'flag', group: 'other', src: { free: 'Flag01Icon' }, en: ['flag', 'milestone', 'goal'], ar: ['علم', 'هدف', 'إنجاز'] },
  { key: 'idea', group: 'other', src: { free: 'Idea01Icon' }, en: ['idea', 'project', 'plan'], ar: ['فكرة', 'مشروع', 'خطة'] },
  { key: 'smile', group: 'other', src: { free: 'SmileIcon' }, en: ['fun', 'personal', 'happy'], ar: ['شخصي', 'مرح', 'سعادة'] },
  { key: 'pet', group: 'other', src: { free: 'PawPrintIcon' }, en: ['pet', 'animals', 'vet'], ar: ['حيوان أليف', 'حيوانات', 'بيطري'] },
  { key: 'cat', group: 'other', src: { free: 'CatIcon' }, en: ['cat', 'pet'], ar: ['قطة', 'قطط'] },
  { key: 'fish', group: 'other', src: { free: 'FishIcon' }, en: ['fish', 'aquarium', 'seafood'], ar: ['سمك', 'أسماك', 'حوض'] },
  { key: 'bird', group: 'other', src: { free: 'BirdIcon' }, en: ['bird', 'pet'], ar: ['طائر', 'عصفور'] },
  { key: 'scissors', group: 'other', src: { free: 'ScissorsIcon' }, en: ['barber', 'salon', 'haircut', 'grooming'], ar: ['حلاقة', 'صالون', 'قص شعر'] },
  { key: 'hairdryer', group: 'other', src: { free: 'HairDryerIcon' }, en: ['salon', 'beauty', 'spa'], ar: ['صالون', 'تجميل', 'سبا'] },
  { key: 'calendar', group: 'other', src: { app: 'CalendarToday' }, en: ['subscription', 'recurring', 'monthly'], ar: ['اشتراك', 'متكرر', 'شهري'] },
  { key: 'clock', group: 'other', src: { app: 'Schedule' }, en: ['time', 'schedule', 'hourly'], ar: ['وقت', 'موعد', 'ساعة'] },
  { key: 'lock', group: 'other', src: { app: 'Lock' }, en: ['locked', 'security', 'private'], ar: ['قفل', 'أمان', 'خاص'] },
  { key: 'circle', group: 'other', src: { app: 'Circle' }, en: ['plain', 'none', 'default', 'simple'], ar: ['بدون', 'افتراضي', 'بسيط'] },
];

/** Section order in the picker. Header text comes from `category_icon_group_<id>` strings. */
export const categoryIconGroups = [
  'food', 'transport', 'shopping', 'home', 'health',
  'travel', 'work', 'money', 'leisure', 'tech', 'other',
];
