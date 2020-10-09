# Praca inżynierska
Tworzę aplikację mobilną, która umożliwia komunikację głosową 1:1 za pomocą WebRTC. 

Jej integralną częścią jest system, który w czasie rzeczywistym poprawia jakość głosu poprzez redukcję artefaktów kompresji dźwięku. W tym celu tworzony jest model uczenia maszynowego oparty na głębokich sieciach neuronowych, wraz ze zbiorem danych treningowych. Model ten działa na urządzeniu mobilnym.

### data
Skrypty do generowania zbiorów danych treningowych.

### mobile
Aplikacja kliencka na system Android.

### models
Modele sieci neuronowych wykorzystywane do poprawy jakości dźwięku.

### server
*Signalling server* do nawiązywania połączeń z użyciem WebRTC, odpowiedzialny także za obsługę zaproszeń do znajomych, potencjalnie autentykację, statusy użytkowników itp.

### webrtc
Zawiera 2 pliki biblioteki WebRTC, które zmodyfikowałem, żeby móc transformować ramki odebranego audio. Oprócz tego paczka `.aar` z biblioteką Androida, zbudowaną wraz z tymi modyfikacjami i pliki licencyjne.
