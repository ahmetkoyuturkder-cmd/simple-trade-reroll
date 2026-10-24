# Simple Trade Reroll — Minecraft Java 26.3 / Fabric

Kaynak kod: [GitHub](https://github.com/ahmetkoyuturkder-cmd/simple-trade-reroll). MIT lisanslıdır.

Köylünün normal ticaret ekranına üç kontrol ekler:

Kontroller, köylü penceresinin yanındaki boş alanda görünür. Hedef listesi arama kutusu ve sayfa düğmeleriyle kullanılır.
Arayüz ve sohbet mesajları oyunun diline göre Türkçe veya İngilizce görünür. Arama sürerken sol panelde deneme sayısı gösterilir. Arama kutusuna yazarken `E` gibi envanter kısayolları ticaret ekranını kapatmaz.

- **Yenile:** Ticaretleri bir defa yeniler.
- **Hedef:** Büyü kitabı, köylünün sattığı eşya (`Sat`) veya köylünün aldığı eşya (`Al`) seçilir. Ardından filtreler ayarlanıp `Ara` düğmesine basılır. `Liste` ile başka hedef seçilebilir.
- **Oto:** Seçili hedefi aramayı yeniden başlatır veya durdurur.

Büyü kitabı hedefinde **Sv** tam seviye eşleşmesidir (`0` = herhangi bir seviye). **≤ Z** zümrüt fiyatının üst sınırıdır (`0` = fiyat fark etmez, `1–64` = en fazla bu kadar zümrüt). Köylünün eşya alıp zümrüt verdiği ticaretlerde zümrüt fiyatı filtresi kullanılmaz. `Ara` düğmesi aramayı başlatır; hedefi seçmek tek başına aramayı başlatmaz.

Arama sırasında ekran açık kalır. Hedef bulunduğunda oyun sohbetinde haber verilir. İstediğin ticareti korumak için köylüyle **bir kez ticaret yap**. Hedef 2000 denemede bulunmazsa arama kendiliğinden durur.

Bu sürüm yalnızca **tek oyunculu** dünyada çalışır. Kullanılmamış, acemi köylünün ticaretleri yenilenebilir. Ticaret yapılmış veya seviye atlamış köylüler engellenir. Ticaret giriş yuvaları boş olmalıdır. Mod iş bloğunu kırmaz; teklifleri doğrudan yeniler.

## Kurulum

`simple-trade-reroll-26.3-1.2.1.jar` dosyasını Fabric 26.3 profilinin `mods` klasörüne koy. Fabric API 0.163.0+26.3 veya uyumlu bir sürümü gerekir. Eski `VillagerReroll` modunu ve bu modun önceki sürümünü devre dışı bırak. Minecraft'ı yeniden başlat.

Dar pencerelerde kontroller `TR` düğmesinden açılan ayrı bir panelde görünür. Arama sürerken düğme deneme sayısını gösterir.

## Kaynak koddan derleme

Java 25 ile bu klasörde `./gradlew build` çalıştır. JAR `build/libs/` içinde oluşur.

## Sınırlar

Hedef araması yalnızca mevcut acemi köylünün üretebildiği ticaretlerde sonuç verir. Örneğin kütüphaneci için büyü kitabı, Fletcher için çubuk satın alma seçilebilir. Büyü seviyesi tam eşleşir; zümrüt filtresi ticarette görünen ödeme miktarına üst sınır uygular. Diğer oyuncuların bulunduğu sunucular desteklenmez.
