package com.minipaint.model;

public enum Tool {
    PENCIL("Crayon", "Dessin libre", "Dessin"),
    ERASER("Gomme", "Effacer sur le calque actif", "Dessin"),
    SPRAY("Aérosol", "Peinture en petites gouttes", "Dessin"),
    FILL("Pot de peinture", "Remplir une région", "Dessin"),
    LINE("Ligne", "Tracer une ligne", "Formes"),
    RECTANGLE("Rectangle", "Tracer un rectangle", "Formes"),
    OVAL("Ellipse", "Tracer une ellipse", "Formes"),
    FILLED_RECT("Rectangle plein", "Rectangle rempli", "Formes"),
    FILLED_OVAL("Ellipse pleine", "Ellipse remplie", "Formes"),
    TRIANGLE("Triangle", "Tracer un triangle", "Formes"),
    ARROW("Flèche", "Tracer une flèche", "Formes"),
    TEXT("Texte", "Insérer du texte", "Autres"),
    EYEDROPPER("Pipette", "Récupérer une couleur", "Autres"),
    SELECT("Sélection", "Sélectionner, déplacer, copier", "Autres");

    private final String label;
    private final String help;
    private final String group;

    Tool(String label, String help, String group) {
        this.label = label;
        this.help = help;
        this.group = group;
    }

    public String label() { return label; }
    public String help() { return help; }
    public String group() { return group; }
}
