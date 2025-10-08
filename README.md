[![JFXCentral](https://img.shields.io/badge/Find_me_on-JFXCentral-blue?logo=googlechrome&logoColor=white)](https://www.jfx-central.com/libraries/pdfviewfx)

# PDFViewFX

A custom control that allows an application to display PDF files. The control utilizes Apache's PDFBox project.

![PDFView](docs/images/pdf-view.png)

## Search
The view has excellent built-in search capabilities.

![PDFView](docs/images/pdf-view-search.png)

## AtlantaFX
If you want to use **_AtlantaFX_** for your application then copy the stylesheet called [pdf-view-atlantafx.css](pdfviewfx-demo/src/main/resources/pdf-view-atlanta.css) 
from the demo module into your project. You will need to add it to the list of stylesheets that you 
are attaching to your application's scene. For more information on **_AtlantaFX_** please see the
[AtlantaFX](https://github.com/mkpaz/AtlantaFX)project.

![PDFView](docs/images/pdf-view-atlantafx.png)
![PDFView](docs/images/pdf-view-search-atlantafx.png)

## Running the demo

You can run the demos using Maven by typing the following line into your terminal:

    mvn javafx:run -f pdfviewfx-demo/pom.xml
