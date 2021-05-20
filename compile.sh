cd src/Java

javac FileLoader.java NetRadio.java Client.java Diffuseur.java Gestionnaire.java

jar cfe ../../Client.jar Client Client*.class NetRadio.class
jar cfe ../../Diffuseur.jar Diffuseur Diffuseur.class NetRadio.class FileLoader.class
jar cfe ../../Gestionnaire.jar Gestionnaire Gestionnaire.class NetRadio.class FileLoader.class

rm *.class