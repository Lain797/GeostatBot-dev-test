ეს არის ჩემი პერსონალური პროექტის Dev ვერსია.
ამოცანა იყო,რადგან საქსტატის საიტი ბევრ ინფორმაციას იტევს, 
უბრალოდ უნდა გამეკეთებინა მარტივი ჩატბოტი,რომელიც 100% სიზუსტიტ მომაწოდებდა სწორ ბმულებს და ექნებოდა სწორი და გამართული ქართული.
ვალიდაციისთვის გამოვიყენე Claude და ლინკების "ბაზა" (ანუ სულ რომ აქტუალური ყოფილიყო) გამოვიყენე Google pse. 

# GeostatBot Backend

This is the Spring Boot backend for the Geostat Chatbot project.

## Tech Stack
* Java 17
* Spring Boot 3
* Google Cloud (Speech-to-Text)
* Anthropic API (Claude)
* ElevenLabs (Text-to-Speech)

## Setup
1. Clone the repository.
2. Create a `.env` file in the root directory (see `.env.example`).
3. Add your API keys to `.env`.
4. Run `./gradlew bootRun`.
