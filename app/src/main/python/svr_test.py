import numpy as np
import sklearn
from joblib import load


redModel = load('red_model.joblib')
yellowModel = load('yellow_model.joblib')

X = np.array([0.495, 0.3544, 0, 0.1121, 0.0192, 0.0188, 6.4545, 1.802, 0.5407, 0.3315, 0.3076, 0.2046])
isRed = true

if isRed:
    y_predict = redModel.predict(X)
else:
    y_predict = yellowModel.predict(X)

print(y_predict)