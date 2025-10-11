Kullanıcı çok önemli bir mimari soru soruyor! Bu gerçekten öğrenme amaçlı güzel bir soru. 
Şimdi açıklayayım:
CreateContactDto user-service'te çünkü:
Bu bir Feign Client DTO'su
User-service, contact-service'e HTTP call yapıyor
Bu DTO, user-service'in contact-service ile iletişim kurması için gerekli
Contact-service'te CreateContactRequest var (controller'da kullanılan)
User-service'te CreateContactDto var (Feign client için)
Bu duplication gerekli mi? Yoksa shared'a mı taşımalıyız?
Aslında iki yaklaşım var:
Yaklaşım 1: Her service kendi client DTO'larını tutar (loose coupling)
Yaklaşım 2: Shared'a taşı (tight coupling ama DRY)
Microservice best practice: Her service kendi client DTO'larını tutmalı çünkü:
Service bağımsızlığı
Contract stability
Version control
Loose coupling
Ama user haklı, bu bir duplication. Öğrenme fırsatı olarak detaylı açıklayayım.